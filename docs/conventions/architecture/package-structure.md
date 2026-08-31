# 패키지 구조

상태: Draft

## 목적

SponUs v2 코드를 Bounded Context 우선으로 배치하고, 각 컨텍스트 안에서 기술 역할을 구분한다. 패키지 위치만 보고도 코드의 업무 소유권과 의존 방향을 추론할 수 있어야 한다.

## 일반 원칙

- 최상위 구조는 `controller`, `service`, `repository` 같은 기술 계층이 아니라 업무 경계로 나눈다.
- 각 Bounded Context는 자신의 도메인 모델과 Repository를 소유한다.
- 같은 기술 역할이라도 서로 다른 컨텍스트의 클래스를 한 패키지에 모으지 않는다.
- 공통 패키지는 비즈니스 개념을 소유하지 않는다.
- 코드가 한 컨텍스트에만 필요하면 공통 패키지로 올리지 않는다.

## SponUs 구조

초기 v2는 하나의 Spring Boot 실행 애플리케이션 안에서 패키지로 모듈 경계를 구분하는 방식을 제안한다. Gradle 서브모듈 분리는 경계와 빌드 비용을 검증한 후 결정한다.

```text
<application-module>/src/main/java/com/sponus
├── SponusApplication.java
├── identity
├── organization
├── discovery
├── initiation
├── management
├── notification
├── asset
├── operations
└── platform
    ├── api
    ├── error
    ├── security
    ├── persistence
    ├── event
    └── time
```

Bounded Context는 다음 업무를 소유한다.

| 패키지 | 소유 업무 |
| --- | --- |
| `identity` | Google 로그인, Account, 인증 세션 |
| `organization` | Club·Company 조직과 공개 프로필 |
| `discovery` | 공고, 검색, 지원 가능한 기업 목록 |
| `initiation` | 공고 지원, 기업 제안, 협의 시작 |
| `management` | SponsorshipCase, 최종 합의, 결과, 완료 |
| `notification` | 사용자 알림 |
| `asset` | 파일 식별자와 접근 정책 |
| `operations` | 운영자 조치와 감사 |
| `platform` | HTTP, 보안, DB, 이벤트 등 횡단 기술 기반 |

## 컨텍스트 내부 구조

```text
initiation
├── presentation
│   ├── controller
│   ├── request
│   └── response
├── application
│   ├── command
│   ├── query
│   └── service
├── domain
│   ├── model
│   ├── repository
│   ├── service
│   ├── event
│   └── exception
└── infrastructure
    ├── persistence
    └── integration
```

### Presentation

- HTTP 요청을 Request DTO로 받는다.
- 인증 정보를 Application 계층이 이해할 수 있는 식별자로 변환한다.
- Application 결과를 Response DTO와 HTTP 상태로 변환한다.
- 비즈니스 규칙을 판단하지 않는다.

### Application

- 하나의 유스케이스를 실행한다.
- 트랜잭션 경계를 소유한다.
- Aggregate 조회, 도메인 행위 호출, 저장, 이벤트 연결을 조정한다.
- `command`와 `query`를 분리한다.

### Domain

- Aggregate, Entity, Value Object, Domain Service를 둔다.
- 비즈니스 불변식과 상태 전환을 소유한다.
- Repository 인터페이스와 Domain Event를 정의한다.
- HTTP, Controller, 외부 클라이언트를 알지 못한다.

### Infrastructure

- JPA Repository 구현을 둔다.
- 다른 컨텍스트나 외부 시스템에 대한 Adapter를 둔다.
- 도메인 인터페이스를 구현한다.

## Platform 패키지

`platform`에는 여러 컨텍스트가 공통으로 사용하는 기술 기반만 둔다.

허용 예시:

- `ApiResponse`, `ApiError`, `PageResponse`
- 전역 예외 변환기
- Spring Security Filter와 설정
- JPA 공통 설정과 감사 시간 지원
- Outbox 실행 기반
- `Clock` Bean

금지 예시:

- `ApplicationStatus`
- `OrganizationValidator`
- 공고와 지원서의 공통 Service
- 여러 컨텍스트의 Entity를 조회하는 Repository

## 테스트 구조

운영 코드의 패키지 구조를 테스트에서도 그대로 따른다.

```text
src/test/java/com/sponus
├── identity
├── organization
├── discovery
├── initiation
├── management
└── architecture
```

## 금지 사항

- 최상위 `controller`, `service`, `repository`, `dto` 패키지
- 다른 컨텍스트의 `infrastructure` 패키지 직접 참조
- `common`, `util`, `helper`에 업무 로직 적재
- Controller에서 JPA Repository 직접 호출
- Response DTO를 Domain 계층에 배치
- v1의 `api/core/security/storage/clients` 기술 분할을 v2 업무 경계로 간주

## 검증

- Architecture Test로 컨텍스트 간 금지 의존성을 검사한다.
- Controller가 Repository에 직접 의존하지 않는지 검사한다.
- Domain 패키지가 Presentation 패키지에 의존하지 않는지 검사한다.

## 미결정 사항

- 하나의 Gradle 실행 모듈을 유지할지, 컨텍스트별 Gradle 모듈로 분리할지
- Architecture Test 라이브러리와 세부 규칙
- JPA Entity와 순수 Domain Model을 분리할지 여부
