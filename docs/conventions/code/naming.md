# 명명 규칙

상태: Draft

## 목적

클래스와 메서드 이름만으로 소유 컨텍스트, 유스케이스, 입출력 역할을 파악할 수 있게 한다.

## 일반 원칙

- 코드 이름은 유비쿼터스 언어를 사용한다.
- 축약어보다 전체 의미를 우선한다.
- 기술 구현보다 업무 의도를 이름에 드러낸다.
- 같은 개념에 여러 단어를 사용하지 않는다.
- 사용자 문구와 내부 enum 이름은 필요에 따라 분리할 수 있지만 매핑을 명시한다.

## 클래스 접미사

| 역할 | 형식 | 예시 |
| --- | --- | --- |
| HTTP Controller | `{Resource}Controller` | `ApplicationController` |
| Command 입력 | `{Verb}{Resource}Command` | `SubmitApplicationCommand` |
| Query 입력 | `{View}Query` | `ApplicationDetailQuery` |
| Application Service | `{Verb}{Resource}Service` | `SubmitApplicationService` |
| Request DTO | `{Verb}{Resource}Request` | `UpdateApplicationRequest` |
| Response DTO | `{View}Response` | `ApplicationDetailResponse` |
| Domain Event | 과거형 사실 | `ApplicationSubmitted` |
| Repository Port | `{Aggregate}Repository` | `ApplicationRepository` |
| JPA Adapter | `{Aggregate}JpaRepository` | `ApplicationJpaRepository` |
| 외부 Adapter | `{Provider}{Purpose}Adapter` | `S3AssetStorageAdapter` |
| 도메인 예외 | `{Reason}Exception` | `ApplicationAlreadySubmittedException` |
| API 오류 enum | `{Context}ErrorCode` | `InitiationErrorCode` |

## 메서드 이름

### Domain 행위

상태 변경의 결과가 아니라 의도를 표현한다.

```java
application.submit(now);
application.withdraw(now);
application.markNotSelected(now);
application.startNegotiation(now);
```

다음과 같은 범용 메서드는 사용하지 않는다.

```java
application.setStatus(SUBMITTED);
application.changeStatus(NOT_SELECTED);
```

### Query

- 존재하지 않으면 예외가 필요한 조회: `get...`
- 존재하지 않을 수 있는 조회: `find...`
- 조건 만족 여부: `exists...`, `has...`, `can...`, `is...`
- 목록 검색: `search...` 또는 `findAll...`

### Command Service

유스케이스 동사를 그대로 사용한다.

```java
submitApplicationService.submit(command);
publishAnnouncementService.publish(command);
```

## 패키지 이름

- 모두 소문자를 사용한다.
- 복수형보다 개념 이름을 사용한다.
- 의미가 겹치는 `domain.application.application` 같은 구조를 피한다.
- 기술 약어를 최상위 업무 패키지로 사용하지 않는다.

## SponUs 용어

- 사용자 표시 용어는 `공고 지원`, `미선정`을 사용한다.
- 내부 상태 enum은 `SUBMITTED`, `NOT_SELECTED`, `NEGOTIATING`처럼 영어로 통일한다.
- `Account`는 인증 주체, `Organization`은 업무 행위 주체를 의미한다.
- `Application`은 동아리의 공고 지원, `Proposal`은 기업의 동아리 제안을 의미한다.
- `SponsorshipCase`는 협의가 시작된 후의 관리 단위다.

## 금지 사항

- 의미가 다른 개념을 같은 이름으로 표현
- `process`, `handle`, `doWork` 같은 모호한 유스케이스 이름
- 모든 서비스 이름을 `{Domain}Service` 하나로 통합
- DTO 이름에 의미 없는 `Dto`만 붙여 입출력 방향을 숨김
- 사용자에게 `거절`, `지원 탈락`, `미선정`을 혼용

## 미결정 사항

- Command Service의 메서드를 `execute`로 통일할지 업무 동사를 사용할지
- Domain Event 이름에 `Event` 접미사를 붙일지
- Query Service를 화면별로 나눌지 컨텍스트 단위로 묶을지
