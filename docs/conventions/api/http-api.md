# HTTP API

상태: Draft

## 목적

SponUs API의 URI, HTTP 메서드, 상태 코드 사용 방식을 통일한다. 비즈니스 행위는 클라이언트가 임의의 상태값을 쓰는 방식이 아니라 서버가 검증하는 명시적 Command로 표현한다.

## 기본 경로

- 모든 공개 API는 `/api/v1` 아래에 둔다.
- 리소스 URI는 소문자 복수 명사를 사용한다.
- 계층 관계는 소유 관계가 API 이해에 필요할 때만 URI에 표현한다.
- URI에 화면 번호나 Controller 이름을 넣지 않는다.

```http
GET /api/v1/announcements
GET /api/v1/announcements/{announcementId}
GET /api/v1/applications/{applicationId}
```

## HTTP 메서드

| 목적 | 메서드 | 성공 상태 |
| --- | --- | --- |
| 조회 | `GET` | `200 OK` |
| 생성 | `POST` | `201 Created` |
| 전체 편집 | `PUT` | `200 OK` |
| 비즈니스 상태 전환 | `POST` | `200 OK` |

- 초기 v2 편집 API는 `PATCH`를 사용하지 않는다.
- `PUT` 요청은 해당 화면에서 편집 가능한 전체 표현을 보낸다.
- 생성 성공 시 `Location` 헤더에 생성된 리소스 URI를 제공한다.
- `ApiResponse<T>`를 유지하기 위해 `204 No Content`를 사용하지 않는다.
- 삭제 API와 삭제 성공 상태는 아직 결정하지 않았다.

## Command API

비즈니스 상태 전환은 클라이언트가 `status`를 직접 전송하지 않고 명시적인 endpoint를 호출한다.

```http
POST /api/v1/announcements/{announcementId}/publish
POST /api/v1/applications/{applicationId}/submit
POST /api/v1/applications/{applicationId}/withdraw
POST /api/v1/applications/{applicationId}/not-select
POST /api/v1/applications/{applicationId}/start-negotiation
```

금지 예시:

```http
PATCH /api/v1/applications/{applicationId}
Content-Type: application/json

{
  "status": "NEGOTIATING"
}
```

Command endpoint는 Aggregate가 현재 상태에서 행위를 수행할 수 있는지 검증한다.

## 상태 코드

| 상태 | 의미 |
| --- | --- |
| `200` | 조회, 수정, 상태 전환 성공 |
| `201` | 리소스 생성 성공 |
| `400` | 요청 형식, 필수값, 자체적으로 모순된 값 |
| `401` | 인증 정보 없음 또는 유효하지 않음 |
| `403` | 인증됐지만 해당 조직 유형에 허용되지 않은 기능 |
| `404` | 리소스 없음 또는 소유권이 없어 존재를 숨겨야 함 |
| `409` | 현재 상태, 중복, 낙관적 락 충돌 |
| `500` | 공개할 수 없는 서버 내부 오류 |

## 조회 규칙

- 단건이 있으면 `200`과 상세 Query DTO를 반환한다.
- 단건이 없거나 조회 권한이 없으면 `404`를 반환한다.
- 목록 결과가 비어 있어도 조회는 성공이므로 `200`과 빈 목록을 반환한다.
- 정상 결과를 `200`과 `data: null`로 표현하지 않는다.

## 금지 사항

- 오류를 `200`으로 반환
- 한 endpoint에서 요청 필드에 따라 서로 다른 업무 명령 수행
- 클라이언트가 임의의 상태 enum을 지정
- URI에 `/get`, `/create`, `/update` 사용
- 조회 endpoint에서 도메인 상태 변경
- 화면 이름이나 Figma 프레임 번호를 URI에 사용

## 테스트 기준

- OpenAPI와 Controller의 메서드·상태 코드를 통합 테스트로 검증한다.
- 역할이 다른 Organization의 Command 호출이 `403`인지 검증한다.
- 다른 조직 소유 리소스 조회가 `404`인지 검증한다.

## 미결정 사항

- 삭제 API의 hard delete, soft delete, archive 정책
- 공개 API와 운영자 API의 별도 prefix
- CORS 허용 Origin과 API Gateway 경로 정책
