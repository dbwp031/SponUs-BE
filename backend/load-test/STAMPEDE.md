# 공고 상세 Cache Aside + Cache Stampede 완화 (멀티 서버 + Redis)

여러 API 서버(app1~3) + nginx LB 뒤에서 공고 상세(`GET /api/v2/announcements/{id}`)에
**수동 Cache Aside** 를 적용하고, 동시 miss 시 **공고별 Redisson RLock + double-check** 로
cache stampede 를 완화한다. 쓰기(수정) 시에는 커밋 이후 캐시를 무효화하고, stale 재삽입에 대비해
**delayed double delete** 를 수행한다. Redis 장애 시에는 **DB fallback** 한다.

## Redis 키 / 값 / TTL
```
cache:v1:announcement:detail:{id}        # 공고 상세 DTO(JSON), TTL = 기본 + 0~jitter
lock:cache:v2:announcement:detail:{id}   # 재생성 락 (Redisson RLock, hash)
metrics:announcement.detail.cache.*      # hit/miss/db-load/lock-acquired/lock-wait/lock-timeout/redis-error (전 인스턴스 합산)
```

## 구성 요소
- `AnnouncementDetailCacheStore` — get/put/evict (캐시 전용 String-String RedisTemplate, **짧은 command timeout**)
- `AnnouncementCacheLock` — `RLock.tryLock(waitTime, leaseTime, MILLISECONDS)` 획득 + 소유 스레드의 `unlock()`(best-effort)
- `AnnouncementQueryService.getAnnouncement` — hit 반환 → miss 시 락 → double-check → DB → put → unlock,
  즉시 획득 실패 시 Pub/Sub 기반 대기 → 획득 후 double-check. 대기 초과 시에도 캐시를 한 번 확인한 뒤 DB fallback
- `CacheRedissonConfig` — 캐시 락 전용 클라이언트. 명령/연결 timeout 기본 250ms, 명령 재시도 0회.
  기존 지원 생성용 Redisson 클라이언트는 `@Primary`로 유지하며 캐시에는 전용 클라이언트를 명시적으로 주입한다.
- `AnnouncementService.updateAnnouncement` + `AnnouncementCacheEvictionListener` — 선삭제 + `AFTER_COMMIT` 즉시 삭제 + 지연 2차 삭제
- 설정: `announcement.cache.*` (ttl-seconds, jitter-seconds, lock-lease-ms, wait-budget-ms, double-delete-delay-ms, stampede-lock-enabled, simulated-db-cost-ms, redis-command-timeout-ms)

`wait-budget-ms` 기본값은 300ms, `lock-lease-ms`는 3000ms다. 명시적 lease를 전달하므로 watchdog 자동 연장은 사용하지 않는다.
Pub/Sub 알림을 받은 요청도 락 획득을 다시 시도하고, 획득 후 캐시를 재확인한다. 애플리케이션의 캐시 폴링 루프는 없다.
대기 초과·lease 만료·Redis 장애 시 중복 DB 조회가 발생할 수 있으므로 조회 1회를 항상 보장하는 락은 아니다.

### 기존 구현에서 전환

기존 v1 문자열 락과 Redisson hash의 `WRONGTYPE` 충돌을 피하기 위해 **락 키만 v2**로 분리했다.
캐시 데이터 키(v1)는 유지한다. 구버전과 신버전 인스턴스가 섞인 동안에는 서로 다른 락을 사용하므로
중복 조회가 생길 수 있다. 실험은 앱 3개를 모두 새 이미지로 교체한 뒤 수행한다.

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

## 기존 SET NX 구현의 기록(전환 전)

아래는 이전 구현에서 문서화한 결과이며 Redisson 전환 후 성능 수치가 아니다.
실험 기본값에는 `COST_MS=200`의 인위적 DB 비용 지연이 포함된다.
현재 저장소에는 p99 648ms → 322ms의 원본 측정 자료가 없다. Redisson의 성능을 주장하려면 별도로 재측정한다.
| 시나리오 | 결과 |
|---|---|
| A 기본 | 5회 중 hit 4 / db-load 1, TTL 유지 |
| B stampede | **naive db-load 60 → lock db-load 1** (60배↓) |
| C Redis 장애 | pause 상태에서도 200(DB fallback), 짧은 시간 |
| D 무효화 | 수정 후 캐시 evict + 1s 뒤 지연 2차 삭제(로그 확인), 새 값 반영 |

## Redisson 회귀 테스트

2026-10-23 로컬 검증: 단위 테스트 10개, 실제 Redis 통합 테스트 3개 통과. `:api:bootJar` 빌드 성공.
3개 독립 클라이언트의 동시 cold miss 60개에서 repository 호출 1회를 확인했다. HTTP p99는 측정하지 않았다.

단위 테스트는 캐시 hit, 획득 후 재확인, Pub/Sub 대기 후 재확인, 대기 초과, Redis 오류,
인터럽트 보존, DB 오류 시 락 해제를 검증한다.

통합 테스트는 실제 Redis와 **서로 다른 Redisson 클라이언트 3개**를 사용한다.
60개 요청 모두가 cold miss를 관측하도록 동기화하고 repository 호출이 1회인지 확인한다.
DB는 대역이며, 앱 서버 3대의 HTTP 성능 테스트가 아니다. 대기/lease는 테스트 안정성을 위해 10초로 설정한다.
서로 다른 공고의 독립성과 lease가 만료된 소유자가 새 소유자의 락을 해제하지 못하는 것도 검증한다.

```bash
# backend 디렉터리, JDK 17 또는 21 (Gradle 8.5 지원 버전)
./gradlew :api:test --tests '*AnnouncementQueryServiceTest'

# 테스트 전용 Redis. 기존 서비스와 겹치지 않는 포트를 사용한다.
docker run --rm -d --name sponus-cache-redisson-test -p 127.0.0.1:16380:6379 redis:7.4-alpine
REDIS_TEST_ADDRESS=redis://127.0.0.1:16380 ./gradlew :api:test \
  --tests '*AnnouncementCacheRedissonIntegrationTest' --rerun-tasks
docker stop sponus-cache-redisson-test
```

`REDIS_TEST_ADDRESS`가 없으면 통합 테스트는 건너뛴다. 통합 테스트는 생성한 공고별 키만 삭제하며 `FLUSHDB`를 호출하지 않는다.

## 정리
```bash
docker compose -f docker-compose.stampede.yml down
```

## 남은 확장(선택)
- stale 재삽입 race 를 CountDownLatch 통합테스트로 강제 재현 → delayed double delete 효과 검증
- Redis 장애 지속 시 로컬 single-flight / DB fallback 동시성 제한, circuit breaker
- k6 지속 부하(load-test/stampede.js)로 p95/hit-ratio 측정
