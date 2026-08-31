# 테스트

상태: Draft

## 목적

모든 코드를 같은 방식으로 테스트하지 않고, 실패 위험에 맞는 테스트 수준을 선택한다. 멀티 인스턴스 정합성과 Bounded Context 경계도 실행 가능한 테스트로 보호한다.

## 테스트 수준

### Unit Test

대상:

- Aggregate 상태 전환
- Domain 불변식
- Value Object 값 검증과 동등성
- Domain Service 정책
- 시간과 마감 규칙

원칙:

- Spring Context 없이 실행한다.
- 고정 Clock을 사용한다.
- 행위와 결과를 업무 언어로 표현한다.

### Integration Test

대상:

- Controller 요청·응답 계약
- Spring Security 인증·인가
- JPA 매핑과 Repository
- DB 마이그레이션과 제약조건
- 트랜잭션과 Outbox
- S3, Google 등 Adapter 계약

실제 DB와 운영 환경에 가까운 설정을 사용하는 것을 제안한다. 구체적인 테스트 컨테이너 도구는 미결정이다.

### Concurrency Test

필수 시나리오:

- 동일 동아리의 같은 공고 동시 지원
- 동일 origin에서 SponsorshipCase 동시 생성
- 동일 지원서의 동시 상태 전환
- Refresh Token 동시 회전
- 두 Outbox Worker의 같은 이벤트 경쟁 처리
- 동일 Integration Event 중복 소비

### Architecture Test

검증 대상:

- Context 간 Entity·Repository 공유 금지
- Presentation에서 Repository 직접 접근 금지
- Domain의 Presentation·Infrastructure 의존 금지
- `platform`에 업무 코드 배치 금지
- 순환 의존 금지

## 테스트 이름

**제안:** 테스트 메서드 이름은 한글 또는 업무 의미가 분명한 영문을 사용할 수 있지만 한 클래스 안에서는 한 방식을 유지한다.

```java
@Test
void 제출된_지원서는_다시_수정할_수_없다() {
}
```

테스트는 구현 메서드 호출보다 업무 조건과 결과를 설명해야 한다.

## 테스트 데이터

- Fixture가 중요한 기본값을 숨기지 않게 한다.
- 복잡한 Aggregate는 Test Data Builder 사용을 검토한다.
- 테스트 간 DB 상태를 공유하지 않는다.
- 현재 시각, 랜덤 ID, 외부 응답을 통제한다.
- 운영 데이터를 테스트에 복사하지 않는다.

## 외부 시스템

- 단위 테스트에서는 Fake 또는 Stub을 사용한다.
- Adapter 통합 테스트에서 실제 SDK 직렬화와 오류 변환을 검증한다.
- 외부 서비스의 일시 장애, timeout, 잘못된 응답을 테스트한다.
- 실제 사용자 Google Token이나 운영 S3 Bucket을 테스트에 사용하지 않는다.

## API 계약

- 성공 상태와 JSON 구조를 검증한다.
- 오류 상태, 오류 코드, `traceId`, `fieldErrors`를 검증한다.
- 빈 목록과 없는 단건을 구분한다.
- 권한이 없는 리소스가 `404`인지 검증한다.
- OpenAPI와 실제 구현의 불일치를 자동 검사하는 방법을 검토한다.

## 금지 사항

- Private 메서드 구현 자체를 테스트
- `Thread.sleep`에 의존한 동시성 테스트
- 순서에 의존하는 테스트
- 실제 시스템 시간에 의존하는 테스트
- H2 결과만으로 운영 DB 제약이 검증됐다고 간주
- 실패한 테스트를 이유 없이 비활성화

## 완료 기준

- 변경된 Domain 규칙의 Unit Test가 통과한다.
- 변경된 HTTP·Security·Persistence 계약의 Integration Test가 통과한다.
- 유일성·회전·상태 경쟁 변경에는 Concurrency Test가 있다.
- 모듈 경계 변경에는 Architecture Test가 있다.
- 관련 전체 테스트가 로컬과 CI에서 재현 가능하다.

## 미결정 사항

- JUnit assertion 라이브러리와 mocking 도구
- Testcontainers 사용 여부와 운영 DB 종류
- Architecture Test 라이브러리
- 커버리지 목표
- API 계약 테스트 도구
- 테스트 메서드 한글·영문 표준
