# 공고 상세 Cache Aside + Cache Stampede 완화 (멀티 서버 + Redis)

여러 API 서버(app1~3) + nginx LB 뒤에서 공고 상세(`GET /api/v2/announcements/{id}`)에
**수동 Cache Aside** 를 적용하고, 동시 miss 시 **공고별 Redis 재생성 락 + double-check** 로
cache stampede 를 완화한다. 쓰기(수정) 시에는 커밋 이후 캐시를 무효화하고, stale 재삽입에 대비해
**delayed double delete** 를 수행한다. Redis 장애 시에는 **DB fallback** 한다.

## Redis 키 / 값 / TTL
```
cache:v1:announcement:detail:{id}        # 공고 상세 DTO(JSON), TTL = 기본 + 0~jitter
lock:cache:v1:announcement:detail:{id}   # 재생성 락 (SET NX PX + Lua unlock)
metrics:announcement.detail.cache.*      # hit/miss/db-load/lock-acquired/lock-wait/lock-timeout/redis-error (전 인스턴스 합산)
```

## 구성 요소
- `AnnouncementDetailCacheStore` — get/put/evict (캐시 전용 String-String RedisTemplate, **짧은 command timeout**)
- `AnnouncementCacheLock` — `SET NX PX` 획득 + 소유자 토큰 Lua 검증 후 삭제(best-effort)
- `AnnouncementQueryService.getAnnouncement` — hit 반환 → miss 시 락 → double-check → DB → put → unlock,
  락 미획득 시 bounded wait → DB fallback, Redis 장애 시 DB fallback
- `AnnouncementService.updateAnnouncement` + `AnnouncementCacheEvictionListener` — 선삭제 + `AFTER_COMMIT` 즉시 삭제 + 지연 2차 삭제
- 설정: `announcement.cache.*` (ttl-seconds, jitter-seconds, lock-lease-ms, wait-budget-ms, double-delete-delay-ms, stampede-lock-enabled, simulated-db-cost-ms, redis-command-timeout-ms)

전용 포트로 격리(기존 로컬 인프라와 무충돌): mysql `3308`, redis `6382`, nginx `8088`.

## 준비 & 기동
```bash
cd backend
JAVA_HOME=<jdk21> ./gradlew :api:bootJar
docker build -t sponus-app:stampede -f Dockerfile.stampede .

# mysql/redis 먼저 → healthy 후 앱
docker compose -f docker-compose.stampede.yml up -d mysql redis
# (mysql healthy 대기)
STAMPEDE_LOCK=true docker compose -f docker-compose.stampede.yml up -d app1 app2 app3 nginx
```
> 실험 파라미터: `STAMPEDE_LOCK`(true=완화/false=stampede 재현), `TTL_SECONDS`(기본 10), `COST_MS`(DB비용 흉내, 기본 200), `JITTER_SECONDS`, `DOUBLE_DELETE_MS`.
> 멀티 인스턴스가 `ddl-auto=update` 를 동시에 실행하면 시작 시 "Duplicate foreign key" WARN 이 날 수 있으나 스키마는 정상 생성된다.

## 시드
```bash
B=http://localhost:8088
curl -s -X POST $B/api/v2/organizations/join -H 'Content-Type: application/json' \
  -d '{"email":"corp@example.com","password":"pw12345!","name":"기업A","organizationType":"COMPANY"}'   # → companyId 1
curl -s -X POST $B/api/v2/me/announcements -H 'Content-Type: application/json' -H 'X-Organization-Id: 1' \
  -d '{"title":"협찬 모집 v1","content":"음료","collaborationType":"SPONSORSHIP"}'                        # → announcementId 1
```
헬퍼: `R(){ docker compose -f docker-compose.stampede.yml exec -T redis redis-cli "$@"; }`

## 테스트

### A. 기본 Cache Aside
```bash
R DEL cache:v1:announcement:detail:1
for m in hit miss db-load; do R DEL metrics:announcement.detail.cache.$m; done
for i in 1 2 3 4 5; do curl -s -o /dev/null $B/api/v2/announcements/1; sleep 0.2; done
R GET metrics:announcement.detail.cache.hit   # 4
R GET metrics:announcement.detail.cache.db-load # 1
```

### B. 동시 miss stampede (naive vs lock)
콜드 캐시에 60개 동시 요청 → `db-load` 비교.
```bash
R DEL cache:v1:announcement:detail:1 metrics:announcement.detail.cache.db-load
D=$(mktemp -d); for i in $(seq 1 60); do ( curl -s -o /dev/null $B/api/v2/announcements/1 ) & done; wait; rm -rf "$D"
R GET metrics:announcement.detail.cache.db-load
# STAMPEDE_LOCK=true  → 1  (single-flight, 나머지는 대기 후 캐시 hit)
# STAMPEDE_LOCK=false → 60 (완화 없음, 전부 DB = stampede)
```
락 토글 전환: `STAMPEDE_LOCK=false docker compose -f docker-compose.stampede.yml up -d --force-recreate app1 app2 app3`

### C. Redis 장애 → DB fallback
```bash
curl -s -o /dev/null $B/api/v2/announcements/1        # 캐시 워밍
docker compose -f docker-compose.stampede.yml pause redis
curl -s -m 8 -o /dev/null -w '%{http_code} %{time_total}s\n' $B/api/v2/announcements/1   # 200, 짧은 시간(빠른 실패→DB)
docker compose -f docker-compose.stampede.yml unpause redis
```

### D. 쓰기 무효화 + delayed double delete
```bash
curl -s $B/api/v2/announcements/1 | grep -o '"title":"[^"]*"'                 # 캐시 워밍
curl -s -X PATCH $B/api/v2/me/announcements/1 -H 'Content-Type: application/json' -H 'X-Organization-Id: 1' \
  -d '{"title":"협찬 모집 v2-수정됨"}'
R TTL cache:v1:announcement:detail:1                                          # -2 (무효화)
curl -s $B/api/v2/announcements/1 | grep -o '"title":"[^"]*"'                 # 새 제목
docker compose -f docker-compose.stampede.yml logs --since 30s app1 app2 app3 | grep -E "AFTER_COMMIT 무효화|delayed double delete"
```

## 실측 결과(예)
| 시나리오 | 결과 |
|---|---|
| A 기본 | 5회 중 hit 4 / db-load 1, TTL 유지 |
| B stampede | **naive db-load 60 → lock db-load 1** (60배↓) |
| C Redis 장애 | pause 상태에서도 200(DB fallback), 짧은 시간 |
| D 무효화 | 수정 후 캐시 evict + 1s 뒤 지연 2차 삭제(로그 확인), 새 값 반영 |

## 정리
```bash
docker compose -f docker-compose.stampede.yml down
```

## 남은 확장(선택)
- stale 재삽입 race 를 CountDownLatch 통합테스트로 강제 재현 → delayed double delete 효과 검증
- Redis 장애 지속 시 로컬 single-flight / DB fallback 동시성 제한, circuit breaker
- k6 지속 부하(load-test/stampede.js)로 p95/hit-ratio 측정
