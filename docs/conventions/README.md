# SponUs Backend Conventions

상태: Draft

## 목적

이 디렉터리는 SponUs v2 백엔드를 구현할 때 반복적으로 지켜야 하는 기술 규칙을 관심사별로 기록한다. 하나의 거대한 컨벤션 문서 대신, 구현 중 필요한 규칙을 빠르게 찾을 수 있도록 아키텍처, API, 영속성, 보안, 통합, 파일, 품질 영역으로 나눈다.

## 문서 상태

- **확정**: 지금까지 합의한 규칙이다. 변경하려면 관련 문서와 구현을 함께 수정한다.
- **제안**: v2 구현을 시작하기 위한 권장 기본값이다. 코드 작성 전에 확정할 수 있다.
- **미결정**: 구현 방향에 영향을 주므로 추가 논의가 필요하다.

각 문서에서 별도 표시가 없는 SponUs 결정은 확정으로 취급한다. `제안`과 `미결정`은 명시적으로 표시한다.

## 규칙의 우선순위

충돌이 발생하면 다음 순서로 판단한다.

1. 현재 사용자가 명시적으로 확정한 결정
2. 최신 PRD와 ADR
3. `sponus-v2.seed.yaml`
4. 이 디렉터리의 컨벤션
5. v2 코드와 테스트
6. v1 코드

v1 코드는 역사적 참고 자료이며 v2 경계나 모델의 기준이 아니다.

## 문서 목록

### Architecture

- [패키지 구조](architecture/package-structure.md)
- [의존성 규칙](architecture/dependency-rules.md)

### Code

- [Java 스타일](code/java-style.md)
- [명명 규칙](code/naming.md)

### API

- [HTTP API](api/http-api.md)
- [요청과 응답](api/request-response.md)
- [예외 처리](api/error-handling.md)
- [페이지네이션과 필터](api/pagination-filtering.md)

### Persistence

- [JPA](persistence/jpa-conventions.md)
- [트랜잭션과 동시성](persistence/transaction-concurrency.md)
- [식별자와 시간](persistence/identifiers-time.md)

### Security

- [인증과 인가](security/authentication-authorization.md)

### Integration

- [이벤트와 멀티 인스턴스](integration/events-multi-instance.md)

### Asset

- [파일 저장](asset/file-storage.md)

### Quality

- [테스트](quality/testing.md)
- [로그와 관측성](quality/logging-observability.md)

## 이 문서에 포함하지 않는 것

다음 내용은 개발 컨벤션이 아니라 별도의 명세가 소유한다.

- 공고, 지원, 미선정, 철회, 합의, 결과 제출 등의 업무 규칙: PRD 또는 `docs/domain/`
- 실제 endpoint별 필드 계약: OpenAPI 문서
- 기술 선택의 배경과 대안: ADR
- 배포 리소스의 구체적인 구성: 인프라 문서

## 변경 원칙

- 규칙을 변경하면 영향을 받는 코드, 테스트, OpenAPI, ADR을 함께 확인한다.
- 구현과 다른 규칙을 문서에 방치하지 않는다.
- 예외가 필요하면 코드에 암묵적으로 우회하지 말고 문서에 이유와 범위를 기록한다.
- `platform`, `common`, `util`을 업무 코드의 임시 보관소로 사용하지 않는다.
