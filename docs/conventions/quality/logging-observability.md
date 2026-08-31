# 로그와 관측성

상태: Draft

## 목적

멀티 인스턴스 환경에서 한 요청과 비동기 이벤트의 흐름을 추적하고, 사용자에게 내부 정보를 노출하지 않으면서 운영 장애의 원인을 찾을 수 있게 한다.

## Trace ID

- 모든 HTTP 요청에 traceId를 부여한다.
- 신뢰할 수 있는 형식의 외부 traceId가 없으면 서버가 생성한다.
- 로그 MDC에 traceId를 포함한다.
- 오류 응답의 `error.traceId`에 동일 값을 제공한다.
- 비동기 이벤트에는 eventId와 correlationId를 전달하는 방식을 제안한다.

## 구조화 로그

**제안:** 운영 로그는 JSON 등 구조화된 형식을 사용한다.

공통 필드 예시:

```text
timestamp
level
service
instanceId
traceId
eventId
context
operation
errorCode
durationMs
```

Account ID와 Organization ID는 필요성과 개인정보 위험을 검토한 후 내부 식별자로만 기록한다.

## 로그 수준

- `DEBUG`: 로컬 진단용 세부 정보, 운영 기본 비활성
- `INFO`: 주요 Command 완료와 시스템 생명주기
- `WARN`: 예상 가능하지만 운영 확인이 필요한 반복 실패와 충돌
- `ERROR`: 서버 내부 오류, 외부 연동 장애, 복구 실패

일반적인 사용자 입력 오류를 모두 ERROR로 기록하지 않는다.

## 민감정보

로그 금지:

- Access Token
- Refresh Token과 Cookie
- Google ID Token
- Authorization Header
- Presigned URL 전체
- 파일 원문
- 이메일 본문
- 스택 트레이스에 포함될 수 있는 개인정보

공개 연락 이메일과 인증 이메일도 목적 없이 로그에 남기지 않는다.

## 감사 기록

일반 애플리케이션 로그와 사용자에게 의미 있는 업무 이력, 운영자 감사 기록을 구분한다.

- 업무 이력은 도메인 데이터로 영구 보존한다.
- 운영자의 민감 데이터 조회와 숨김·복원·정지 조치는 감사 기록을 남긴다.
- 감사 기록은 일반 로그 보존 기간에 의존하지 않는다.
- 감사 기록을 사용자가 작성한 업무 데이터 수정 수단으로 사용하지 않는다.

## 핵심 지표 제안

### HTTP

- 요청 수, latency, 상태 코드 비율
- endpoint별 `4xx`, `5xx`
- 인증 실패와 권한 실패

### DB와 동시성

- 낙관적 락 충돌 횟수
- Unique Constraint 충돌 횟수
- Connection Pool 사용량
- 느린 Query

### Event

- 미처리 Outbox 수
- 가장 오래된 Outbox 지연 시간
- 소비 실패와 재시도 횟수
- Inbox 중복 감지 횟수

### Asset과 인증

- S3 업로드·다운로드 URL 발급 실패
- Google Token 검증 실패
- Refresh Token 회전 실패와 재사용 탐지

## Health Check

- Liveness는 프로세스가 요청을 처리할 수 있는지 판단한다.
- Readiness는 필수 의존성 상태를 고려한다.
- 무거운 외부 호출을 모든 Liveness 요청에서 실행하지 않는다.
- 상세 내부 상태를 공개 Health endpoint에 노출하지 않는다.

## 금지 사항

- `System.out.println`
- 토큰과 서명 URL 출력
- 모든 예외를 ERROR로 기록
- 같은 예외를 여러 계층에서 중복 로깅
- 사용자 메시지만 남기고 내부 원인과 traceId를 잃음
- 업무 감사 이력을 일반 로그로만 보존

## 테스트 및 검증

- 오류 응답과 로그 MDC의 traceId가 같은지 검증한다.
- 보안 관련 값이 로그에 포함되지 않는지 테스트 또는 리뷰한다.
- Outbox 지연과 실패 지표를 로컬에서 관찰할 수 있게 한다.
- 멀티 인스턴스 로그에 instanceId가 포함되는지 검증한다.

## 미결정 사항

- 로그 포맷과 수집 도구
- traceId 표준과 분산 추적 도구
- 메트릭 수집·대시보드·알림 도구
- 로그와 감사 기록 보존 기간
- 예상 가능한 업무 예외의 INFO/WARN 기준
