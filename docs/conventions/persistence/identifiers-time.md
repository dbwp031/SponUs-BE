# 식별자와 시간

상태: Draft

## 목적

ID와 시간 타입의 의미를 일관되게 유지하고, 멀티 인스턴스·시간대·테스트 환경에서 발생하는 모호함을 줄인다.

## 식별자

### SponUs 결정

- Entity 식별자는 Java `Long`을 사용한다.
- DB 컬럼은 `BIGINT AUTO_INCREMENT`를 사용한다.
- API에서는 JSON number로 전달한다.
- 다른 Bounded Context에는 Entity 참조가 아니라 ID를 전달한다.
- ID에 업무 의미를 인코딩하지 않는다.

```java
public record OrganizationId(Long value) {
}
```

ID Value Object를 실제로 사용할지는 구현 복잡도를 확인한 후 결정한다. 사용하지 않더라도 `Long organizationId`처럼 대상이 드러나는 변수명을 사용한다.

### 확장 고려

현재는 여러 애플리케이션 인스턴스가 하나의 쓰기 DB를 공유하므로 Auto Increment로 충분하다. 멀티 Writer DB나 샤딩이 실제 요구가 되면 UUID, ULID, Snowflake 계열 ID로 마이그레이션한다.

## 시간 타입

| 의미 | Java 타입 | 기준 |
| --- | --- | --- |
| 행사 일정, 공고 마감 | `LocalDateTime` | `Asia/Seoul` |
| 생성, 수정, 이벤트, 토큰 만료 | `Instant` | UTC |
| 날짜만 의미가 있는 값 | `LocalDate` | 업무 달력 |

### LocalDateTime

- 행사 시작과 같이 한국 서비스 지역의 벽시계 시간이 업무 의미인 값에 사용한다.
- API 필드 설명에 `Asia/Seoul` 기준임을 명시한다.
- 시스템 감사 시각에 사용하지 않는다.

### Instant

- 생성·수정·제출·승인 시각에 사용한다.
- 이벤트 발생 시각과 토큰 만료 시각에 사용한다.
- DB와 메시지에서는 UTC 기준으로 저장한다.
- API에서는 `2026-09-01T03:20:00Z` 형식으로 표현한다.

### LocalDate

- 시간과 시간대가 업무 의미에 없는 날짜에 사용한다.
- 생일 등 현재 수집하지 않는 개인정보 필드를 추가하는 근거로 사용하지 않는다.

## Clock

- 현재 시각은 주입된 `Clock`으로 생성한다.
- 운영 기본 Clock은 UTC를 제안한다.
- 업무 지역 시간이 필요하면 명시적인 `ZoneId.of("Asia/Seoul")` 변환을 사용한다.
- 테스트에서는 `Clock.fixed`를 사용한다.

```java
@Bean
Clock clock() {
    return Clock.systemUTC();
}
```

```java
Instant now = clock.instant();
LocalDateTime businessNow = LocalDateTime.ofInstant(now, SEOUL_ZONE);
```

## DB 컬럼

- 시간 컬럼명은 의미를 드러내는 과거형을 사용한다: `created_at`, `submitted_at`, `approved_at`.
- `created_at`, `updated_at`만으로 업무 이력을 대체하지 않는다.
- 승인, 철회, 완료처럼 중요한 사실은 별도 시간 필드를 사용한다.
- DB 타입과 JDBC 시간대 설정은 마이그레이션 문서에서 고정한다.

## 금지 사항

- 업무 코드에서 `LocalDateTime.now()` 또는 `Instant.now()` 직접 호출
- 같은 의미의 시간을 Entity마다 다른 타입으로 사용
- offset 없는 문자열을 UTC 시각으로 간주
- `updatedAt`만으로 모든 상태 변경 시점을 추론
- ID를 문자열로 변환해 업무 구분자를 덧붙임

## 테스트 기준

- 고정 Clock으로 경계 시간과 마감 규칙을 테스트한다.
- UTC와 `Asia/Seoul` 변환을 테스트한다.
- JSON 직렬화 형식을 테스트한다.
- ID 생성과 영속화 후 타입을 검증한다.

## 미결정 사항

- MySQL 또는 PostgreSQL 선택에 따른 실제 시간 컬럼 타입
- ID Value Object 적용 범위
- 멀티 Writer 전환을 판단할 기준
