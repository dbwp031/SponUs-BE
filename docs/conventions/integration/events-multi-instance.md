# 이벤트와 멀티 인스턴스

상태: Draft

## 목적

두 대 이상의 Stateless 애플리케이션 인스턴스에서 이벤트 유실과 중복 부수 효과를 방지하고, 원본 도메인 상태와 Projection의 책임을 분리한다.

## 멀티 인스턴스 원칙

- 정합성에 필요한 상태를 인스턴스 메모리에만 저장하지 않는다.
- 모든 인스턴스는 공유 DB와 영구 파일 저장소를 사용한다.
- Java `synchronized`, 로컬 캐시, 메모리 Set으로 업무 유일성을 보장하지 않는다.
- 스케줄러와 Worker가 여러 인스턴스에서 실행될 가능성을 전제로 한다.
- Redis, 분산 Lock, 별도 Message Broker는 유스케이스가 정당화할 때 도입한다.

## 이벤트 종류

### Domain Event

- 한 Bounded Context 내부에서 발생한 업무 사실이다.
- 과거형 이름을 사용한다.
- Aggregate 상태가 실제로 변경된 경우에만 생성한다.

예:

```text
ApplicationSubmitted
NegotiationStarted
AgreementApproved
ResultSubmitted
```

### Integration Event

- 다른 컨텍스트가 안정적으로 소비할 공개 계약이다.
- 내부 Entity를 포함하지 않는다.
- ID, 필요한 불변 데이터, 발생 시각, 계약 버전을 포함한다.
- 발행 후 필드 의미를 임의로 변경하지 않는다.

## 이벤트 Envelope 제안

```java
public record IntegrationEventEnvelope<T>(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    T payload
) {
}
```

- `eventId`는 소비자 중복 제거에 사용한다.
- `occurredAt`은 `Instant`다.
- `eventVersion`은 Event Schema 버전이며 Entity `lockVersion`과 다르다.

## Outbox

- 도메인 상태 변경과 Outbox 레코드 저장을 같은 DB 트랜잭션에서 수행한다.
- 커밋되지 않은 상태의 이벤트를 외부에 발행하지 않는다.
- Worker는 미처리 Outbox를 조회하여 발행한다.
- 처리 성공을 영구 기록한다.
- 인스턴스 종료 후에도 다른 Worker가 재처리할 수 있어야 한다.

## Inbox와 소비자 멱등성

- 소비자는 `eventId`를 기준으로 이미 처리한 이벤트인지 검사한다.
- 중복 전달되어도 Notification 또는 Projection이 중복 생성되지 않아야 한다.
- 처리 기록과 부수 효과를 가능한 한 같은 트랜잭션에서 저장한다.
- 실패한 이벤트는 재시도할 수 있고 운영자가 원인을 추적할 수 있어야 한다.

## SponUs 이벤트 사용

- Management 상태 변경은 Notification에 Integration Event로 전달한다.
- Management 상태 변경은 Dashboard Projection을 갱신한다.
- 공고 변경은 관련 사용자 알림 생성으로 이어질 수 있다.
- 알림과 Dashboard는 편의를 위한 Read Model이며 도메인 원본 상태를 대신하지 않는다.

## 일관성 모델

- 도메인 Command 응답은 원본 Aggregate의 커밋 결과를 기준으로 한다.
- Notification과 Dashboard는 최종적 일관성을 허용한다.
- Command 성공 직후 Projection이 아직 갱신되지 않을 수 있음을 UI와 API에서 고려한다.
- 반드시 즉시 필요한 값은 Command 결과 또는 원본 Query에서 조회한다.

## 금지 사항

- DB 커밋 전에 FCM, 이메일 등 외부 알림 발송
- 인메모리 Application Event만으로 내구성 필요한 통합 처리
- Event Payload에 JPA Entity 포함
- 소비자 실패를 삼키고 처리 완료로 표시
- Event ID 없이 at-least-once 소비
- 알림 테이블을 SponsorshipCase의 상태 원본으로 사용

## 테스트 기준

- 상태 변경과 Outbox 저장의 원자성을 검증한다.
- Worker 중단 후 미처리 이벤트가 복구되는지 검증한다.
- 동일 Event 중복 소비 시 결과가 한 번만 생성되는지 검증한다.
- 두 Worker가 동시에 같은 Outbox를 처리하는 시나리오를 검증한다.
- Projection 지연이 Command의 원본 상태를 훼손하지 않는지 검증한다.

## 미결정 사항

- Outbox polling 방식과 주기
- DB Lock 또는 claim 컬럼을 사용한 Worker 경쟁 처리 방식
- 재시도 횟수, backoff, dead-letter 정책
- Event Payload 직렬화 형식과 Schema 버전 관리 방식
- 별도 Message Broker 도입 기준
