# 의존성 규칙

상태: Draft

## 목적

계층과 Bounded Context 사이의 의존 방향을 제한하여 한 기능의 변경이 전체 시스템으로 전파되는 것을 막는다.

## 계층 의존 방향

```text
Presentation → Application → Domain
Infrastructure ─────────────→ Domain/Application Port
```

- Presentation은 Application 유스케이스만 호출한다.
- Application은 Domain 객체와 자신이 소유한 Port에 의존한다.
- Domain은 자신의 업무 모델과 Java 기본 타입에 집중한다.
- Infrastructure는 Domain 또는 Application이 선언한 인터페이스를 구현한다.
- 의존 방향을 맞추기 위해 Domain이 Infrastructure 구현을 import하지 않는다.

## Bounded Context 간 규칙

- 한 컨텍스트는 다른 컨텍스트의 mutable Entity를 import하지 않는다.
- 한 컨텍스트는 다른 컨텍스트의 JPA Repository를 직접 호출하지 않는다.
- 다른 컨텍스트의 DB 테이블을 QueryDSL이나 SQL로 직접 조인하지 않는다.
- 동기 통신은 명시적인 Application Contract를 사용한다.
- 비동기 통신은 커밋된 Integration Event를 사용한다.
- 과거 사실이 필요하면 원본 Entity 참조 대신 불변 Snapshot을 저장한다.

## 허용하는 통합 데이터

- `AccountId`, `OrganizationId`, `AnnouncementId`, `AssetId` 같은 식별자
- 발행 시점의 `AnnouncementSnapshot`
- 공개 프로필 조회 DTO
- `StartSponsorshipCaseCommand` 같은 명시적 계약
- `ApplicationSubmitted`, `CaseStatusChanged` 같은 Integration Event

계약 DTO는 공급 컨텍스트의 내부 Entity 구조와 분리한다.

## Domain과 기술 관심사

다음 내용은 Domain 모델 안에 넣지 않는다.

- `HttpStatus`
- `ResponseEntity`
- JWT 파싱
- S3 Key와 Presigned URL 생성
- JPA Query 구현
- 재시도 스케줄러
- 로그 포맷

Domain은 `지원서를 제출할 수 있는가`를 판단하고, Presentation은 그 실패를 `409 Conflict`로 표현한다.

## 트랜잭션과 이벤트 의존성

- 도메인 상태 변경과 Outbox 저장은 같은 트랜잭션에서 처리한다.
- 외부 알림 발송과 Read Model 갱신은 커밋 이후 수행한다.
- 이벤트 소비 실패가 원본 도메인 트랜잭션을 되돌리지 않는다.
- 소비자는 동일 이벤트를 여러 번 받아도 결과가 중복되지 않아야 한다.

## 금지 사항

- 순환 의존
- Context A Service가 Context B Repository를 직접 호출
- Context A Entity가 Context B Entity와 JPA 연관관계 구성
- Domain Event Listener에서 커밋 전 외부 네트워크 호출
- `platform`이 업무 흐름을 조정하거나 도메인 상태를 판단

## 검증

- Architecture Test로 패키지 의존 방향을 검사한다.
- Context 간 공개 계약 외 import를 실패시킨다.
- 통합 테스트로 상태 변경과 Outbox 저장의 원자성을 검증한다.

## 미결정 사항

- 각 컨텍스트의 공개 Application Contract 배치 위치
- 컨텍스트 간 동기 호출에 사용할 인터페이스 명명 방식
- 컴파일 수준 격리를 위한 Gradle 모듈 분리 시점
