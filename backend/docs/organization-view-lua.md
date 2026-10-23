# 기업 프로필 조회수: Lua 기반 Redis 집계

기업 프로필 조회 시 Redis에서 당일 중복 조회를 제외하고 증가분을 누적한다. 스케줄러가 증가분을 꺼내 DB의 `viewCount`에 더한다. 공고 상세 캐시와는 별도의 기능이다.

## 키와 수명

| 키 | 자료형 | 역할 | 만료 |
| --- | --- | --- | --- |
| `organization:view:viewers:{기업ID}` | Set | 오늘 조회한 조직 ID | 다음 자정 |
| `organization:view:pending:{기업ID}` | String 정수 | DB에 아직 반영하지 않은 증가분 | TTL 없음 |
| `organization:view:updated` | Set | 집계할 기업 ID | TTL 없음 |

위 표의 `{기업ID}`는 설명용 자리표시자다. 실제 키는 `organization:view:viewers:123`처럼 중괄호 없이 생성한다. 기존 키 이름과 String 직렬화 방식을 유지한다. 자기 기업의 프로필 조회는 기존 `CompanyService`에서 제외한다.

## 조회 요청

`OrganizationViewRedisRepository.addViewer()`가 `add-viewer.lua`를 한 번 실행한다.

1. Set 자료형과 만료 시각을 검증한다.
2. 이미 조회한 조직이면 counter를 증가시키지 않고 종료한다.
3. 신규 조회라면 `INCR pending → SADD viewers → EXPIREAT viewers → SADD updated`를 실행한다.

스크립트 실행 도중 다른 Redis 명령이 끼어들 수 없으므로, 여러 앱 인스턴스가 같은 조회자를 동시에 처리해도 한 번만 증가한다. `INCR`을 먼저 실행하는 이유는 counter가 잘못된 자료형이거나 범위를 초과했을 때 조회자를 먼저 기록해 버리지 않기 위해서다. Lua는 실행을 격리하지만, 실행 중 오류가 발생했다고 앞선 쓰기를 자동으로 롤백하지는 않는다.

자정은 기존과 동일하게 JVM의 기본 시간대를 기준으로 계산한다. 운영 앱들의 시간대와 시계를 일치시켜야 한다. 계산한 자정을 Redis 도착 전에 이미 지난 요청은 쓰기 전에 거부되며, 재시도 시 새 자정을 계산한다. 이 변경에는 자동 재시도 기능이 포함되지 않는다.

## DB 반영

스케줄러의 `fixedDelay = 60초`는 이전 실행이 끝난 후 60초를 기다린다는 뜻이다. 실행 시간과 실패 재시도에 따라 실제 반영 지연은 60초보다 길 수 있다.

```text
pop-updated.lua: 집계 대상 Set 조회 + 삭제
  → 기업별 GETDEL pending
  → DB 트랜잭션에서 view_count = view_count + 증가분
  → 커밋 성공: 완료
  → 롤백 확인: restore-pending.lua로 증가분과 집계 대상을 복원
```

- `pop-updated.lua`는 `SMEMBERS → DEL`을 묶어 여러 스케줄러가 같은 시점의 대상 목록을 중복 수거하지 않게 한다. 이후 새 요청이 등록한 대상은 다음 수거 대상으로 남는다.
- counter는 원래부터 원자 명령인 `GETDEL`을 사용하므로 유지한다. 집계 중 새 조회가 발생하면 같은 기업이 다시 대상 목록에 들어갈 수 있지만, 하나의 counter 증가분을 두 작업이 함께 가져가지는 않는다.
- DB 반영은 기업별 UPDATE다. 모든 기업을 하나의 SQL로 갱신하는 구조는 아니다.
- Spring 트랜잭션의 `afterCompletion`에서 **롤백이 확인된 경우**만 복원한다. 외부 트랜잭션이 서비스 반환 후 롤백되는 경우도 포함한다.
- 복원은 `SET` 대신 `INCRBY`를 사용하므로 집계 중 쌓인 새 조회수를 덮어쓰지 않는다. 아직 처리하지 않은 기업도 집계 대상으로 재등록한다.
- 일일 조회자 Set은 DB 반영 시 삭제하지 않는다. 기존 자정 전체 삭제 스케줄러는 제거하고 TTL로만 만료시킨다. 전체 삭제가 자정 이후 새로 생성된 Set까지 지우는 일을 피하기 위해서다.

## 보장 범위와 운영 조건

Redis 내부 연산의 원자성과 확인된 DB 롤백에 대한 복구를 개선했다. **Redis와 MySQL 전체를 하나의 트랜잭션으로 묶거나 정확히 한 번 반영을 보장하지는 않는다.**

- Redis 수거 이후 프로세스가 강제 종료되면, 메모리에만 보관한 대상 또는 증가분을 복구하지 못할 수 있다.
- Redis 명령의 성공 응답을 받지 못한 경우 실제 실행 여부가 불명확할 수 있다. DB 커밋 결과가 불명확한 경우에는 중복 반영을 피하기 위해 자동 복원하지 않고 오류와 증가분을 기록한다.
- 롤백 후 Redis 복원도 실패하면 오류에 기업 ID와 증가분을 기록한다. 자동으로 영속 재시도하는 큐는 없다.
- 이 범위까지 보장하려면 소비 중인 배치를 Redis 등에 남겨 재수거하고, DB에는 배치 ID를 이용한 중복 반영 방지를 함께 설계해야 한다.
- 전역 `updated` 키와 기업별 키를 함께 실행하므로 현재 스크립트는 단일 Redis 또는 Sentinel 구성에 맞는다. Redis Cluster에서는 키들이 서로 다른 슬롯에 위치할 수 있어 그대로 실행할 수 없다.
- 미반영 증가분과 중복 제외 Set은 버려도 되는 일반 캐시가 아니다. 캐시용 LFU/LRU eviction 대상과 분리하고 메모리 부족·Redis 장애에 대한 운영 정책을 별도로 정해야 한다. 이 변경에서 Redis 인프라 설정을 변경하지는 않았다.
- 대상 목록 수거는 전체 Set 크기에 비례한다. 대상이 매우 많아져 스크립트 실행 시간이 길어진다면 버킷 분할이나 제한된 크기의 수거 방식으로 바꿔야 한다.

## 검증

실제 Redis를 사용하는 저장소 테스트 8개와 H2 JDBC 트랜잭션을 사용하는 서비스 테스트 4개를 추가했다.

- 독립 Redis 클라이언트 3개의 동일 조회자 200건 요청: 증가분 1
- 서로 다른 조회자 200건 요청: 증가분 200
- 집계기 3개의 동시 대상 수거: 같은 목록의 중복 수거 없음
- 작성자 3개와 집계기 3개의 동시 실행: 총 600건 요청 중 고유 조회자 200명 반영
- 집계 후에도 당일 중복 제외 유지, 복원 시 새 증가분 보존, 잘못된 counter 자료형, TTL 검증
- DB 커밋, 중간 실패 후 전체 롤백과 재시도, 외부 트랜잭션 롤백, 미처리 기업의 재등록 검증

Redis 테스트는 아래 환경 변수가 없으면 건너뛴다. **반드시 테스트 전용 Redis를 사용한다.** 대상 목록을 수거하는 테스트이므로 운영·개발 공유 Redis를 연결하면 안 된다.

```bash
# backend 디렉터리에서 실행. Redis 6.2 이상(GETDEL 지원), JDK 17 이상.
REDIS_TEST_ADDRESS=redis://127.0.0.1:6379 ./gradlew \
  :storage:redis:test --tests '*OrganizationViewRedisRepositoryIntegrationTest' \
  :api:test --tests '*OrganizationViewRollbackTest' \
  :api:bootJar
```

이 테스트는 Redis 경합과 DB 트랜잭션 복구 검증이다. ECS 앱 태스크 3개를 통한 HTTP 부하 테스트나 과거 p99 성능 수치의 재측정은 아니다. 서비스 테스트의 Redis는 대역을 사용하며, 실제 Lua 실행은 저장소 테스트에서 검증한다.

참고: [Redis Lua 실행 보장](https://redis.io/docs/latest/develop/programmability/eval-intro/), [GETDEL](https://redis.io/docs/latest/commands/getdel/), [Spring 스케줄링](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).
