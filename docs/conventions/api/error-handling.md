# 예외 처리

상태: Draft

## 목적

도메인 실패, 요청 검증 실패, 보안 실패, 인프라 장애를 구분하고 일관된 HTTP 오류 계약으로 변환한다.

## 기본 구조

```java
public record ApiError(
    String code,
    String message,
    String traceId,
    List<FieldErrorResponse> fieldErrors
) {
}
```

- 외부 오류 코드는 `E1234` 형식을 사용한다.
- 내부 코드에서는 `APPLICATION_ALREADY_SUBMITTED`처럼 의미 있는 이름을 사용한다.
- API 응답에 Java 예외 클래스명이나 스택 트레이스를 노출하지 않는다.
- 한 번 공개한 오류 코드는 다른 의미로 재사용하지 않는다.

## 오류 코드 구간

| 범위 | 소유 영역 |
| --- | --- |
| `E0000-E0999` | 공통, 검증, 동시성 |
| `E1000-E1999` | Identity |
| `E2000-E2999` | Organization |
| `E3000-E3999` | Discovery |
| `E4000-E4999` | Initiation |
| `E5000-E5999` | Management |
| `E6000-E6999` | Asset |
| `E7000-E7999` | Notification, Dashboard |
| `E8000-E8999` | Platform Operations |
| `E9000-E9999` | Infrastructure, Internal Server Error |

공통 코드 초안:

- `E0001`: 요청값 검증 실패
- `E0002`: 낙관적 락 충돌
- 나머지 코드는 endpoint 상세 설계에서 확정한다.

## 예외 분류

### Request Validation

- JSON 파싱 실패
- 필수값 누락
- 문자열 길이 초과
- 허용하지 않는 enum
- 요청 내부의 날짜 범위 역전

`400 Bad Request`와 `fieldErrors`를 반환한다.

### Domain Exception

- 이미 제출된 지원서 수정
- 현재 상태에서 허용되지 않는 전환
- 승인된 합의서 수정

Domain Exception은 `HttpStatus`, `ResponseEntity`를 import하지 않는다. Presentation Adapter가 도메인 실패를 HTTP 상태와 외부 오류 코드로 변환한다.

### Application Exception

- 조회 대상 없음
- 소유 Organization 불일치
- 동일 공고 재지원
- 유스케이스 선행 조건 불충족

존재 여부를 숨겨야 하는 소유권 실패는 `404`로 변환한다.

### Concurrency Exception

- `OptimisticLockingFailureException`
- Unique Constraint 충돌

현재 상태와 충돌하므로 `409 Conflict`로 변환한다. 동일한 Unique Constraint 충돌이라도 제약조건 이름을 기준으로 안정적인 업무 오류 코드로 변환한다.

### Security Exception

- 인증 실패: `401`
- 조직 역할 불일치: `403`

Spring Security Filter Chain에서 발생한 오류도 일반 API와 동일한 `ApiResponse` 형식으로 반환한다.

### Infrastructure Exception

- DB 연결 실패
- S3 장애
- 외부 인증 공급자 장애
- 직렬화할 수 없는 내부 오류

사용자에게 내부 메시지를 노출하지 않고 `E9xxx`와 일반화된 메시지를 반환한다. 원인은 traceId와 함께 서버 로그에 남긴다.

## 변환 흐름

```text
Domain/Application/Infrastructure Exception
                    ↓
          GlobalExceptionHandler
                    ↓
       ApiResponse<Void> + HTTP Status
```

필터 계층의 인증·인가 오류는 각각 Authentication Entry Point와 Access Denied Handler에서 동일한 응답 계약으로 변환한다.

## 로깅 수준 제안

- `400`, 예상 가능한 `404`, 업무 `409`: `INFO` 또는 `WARN` 중 정책 확정 필요
- 인증 공격 가능성이 있는 반복 실패: `WARN`
- `500`, 외부 연동 장애: `ERROR`
- 스택 트레이스는 서버 로그에만 남긴다.
- 토큰, 쿠키, 개인정보, 파일 URL 서명을 로그에 남기지 않는다.

## 금지 사항

- 모든 예외를 하나의 `CustomException`으로 뭉쳐 의미를 숨김
- Domain Error Code에 Spring `HttpStatus` 직접 포함
- DB 예외 메시지를 사용자에게 그대로 반환
- `Exception`을 잡아 성공 응답으로 변경
- 같은 오류 코드에 여러 HTTP 상태 사용
- Controller마다 개별 try-catch로 동일 오류 변환

## 테스트 기준

- 오류 코드, HTTP 상태, 메시지, JSON 구조를 통합 테스트로 검증한다.
- 다른 조직 리소스가 `403`이 아니라 `404`인지 검증한다.
- 낙관적 락과 Unique Constraint 충돌이 `409`인지 검증한다.
- 예상하지 못한 예외에 내부 메시지가 노출되지 않는지 검증한다.

## 미결정 사항

- Error Code와 HTTP 상태의 매핑 객체 위치
- 업무 오류 메시지의 다국어 지원 여부
- `fieldErrors`가 없을 때 빈 배열 또는 생략 정책
- 예상 가능한 업무 실패의 로그 레벨
