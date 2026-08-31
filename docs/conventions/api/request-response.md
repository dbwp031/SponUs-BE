# 요청과 응답

상태: Draft

## 목적

클라이언트와 서버가 주고받는 JSON 구조를 통일하고, Domain·JPA 모델이 외부 계약으로 유출되지 않게 한다.

## 공통 응답

모든 성공과 실패 응답은 `ApiResponse<T>`로 감싼다.

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error
) {
}
```

성공 예시:

```json
{
  "success": true,
  "data": {
    "id": 123,
    "status": "DRAFT"
  },
  "error": null
}
```

실패 예시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "E4001",
    "message": "지원서를 찾을 수 없습니다.",
    "traceId": "01J7...",
    "fieldErrors": []
  }
}
```

## Request DTO

- HTTP Request DTO는 `presentation.request`에 둔다.
- Jakarta Validation은 Request DTO에서 수행한다.
- Request DTO를 Domain 메서드에 그대로 전달하지 않는다.
- Controller에서 Request를 Application Command 또는 Query로 변환한다.
- 서버가 인증 정보로 알 수 있는 `accountId`, `organizationId`를 요청 본문으로 받지 않는다.
- 클라이언트가 S3 Key, 작성자 ID, 상태값을 임의로 지정하게 하지 않는다.

```java
public record UpdateApplicationRequest(
    @NotBlank @Size(max = 1_000) String eventScale,
    @NotBlank @Size(max = 1_000) String promotionPlan,
    @NotBlank @Size(max = 1_000) String motivation,
    Long attachmentAssetId
) {
}
```

## PUT 표현

- 편집 화면의 모든 편집 가능 필드를 전송한다.
- 필수값을 누락하면 `400`이다.
- 선택값 삭제는 `null`로 표현한다.
- 컬렉션을 비우는 것은 빈 배열 `[]`로 표현한다.
- 파일 바이너리는 PUT 본문에 포함하지 않고 먼저 업로드하여 받은 `AssetId`를 보낸다.

## Command 성공 응답

Command는 전체 상세 모델 대신 다음 화면 전환에 필요한 핵심 결과를 반환한다.

```java
public record CommandResultResponse(
    Long id,
    String status,
    Instant updatedAt
) {
}
```

생성은 `createdAt`, 변경은 `updatedAt`을 사용한다. 실제 응답 DTO 이름은 해당 유스케이스의 의미를 드러내야 하므로 범용 DTO 사용 여부는 구현 시 검토한다.

## 단건 Query 응답

- 화면과 유스케이스가 필요한 필드를 명시적으로 정의한다.
- 지연 로딩 Entity를 직렬화하지 않는다.
- 내부 `lockVersion`, 토큰 해시, S3 Key를 노출하지 않는다.
- 다른 컨텍스트 정보는 공개 계약 DTO나 Snapshot으로 조합한다.

## 목록 응답

페이지 목록은 Spring Data의 `Page`를 직접 반환하지 않고 독립된 계약을 사용한다.

```java
public record PageResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
}
```

결과가 없을 때:

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false
  },
  "error": null
}
```

## JSON 타입

- ID는 JSON number로 표현한다.
- enum은 대문자 문자열로 표현한다.
- `Instant`는 UTC ISO-8601 문자열로 표현한다. 예: `2026-09-01T03:20:00Z`
- `LocalDateTime`은 offset이 없는 ISO-8601 문자열로 표현하고 필드 설명에 `Asia/Seoul` 의미를 적는다.
- `LocalDate`는 `YYYY-MM-DD`로 표현한다.
- 정상적인 컬렉션은 `null`이 아니라 빈 배열로 표현한다.

## 금지 사항

- JPA Entity 직접 반환
- Spring Data `Page` 직접 반환
- 성공과 실패에서 같은 필드의 타입이 달라지는 응답
- 정상 단건을 `data: null`로 표현
- Response DTO 안에 Repository나 Domain Service 호출
- 클라이언트가 권한 결정용 Organization ID를 제공

## 테스트 기준

- JSON 직렬화 형식과 필드명을 Controller 통합 테스트로 고정한다.
- 빈 목록 응답이 `items: []`인지 검증한다.
- 내부 필드가 JSON에 포함되지 않는지 검증한다.

## 미결정 사항

- `fieldErrors`가 없을 때 빈 배열과 필드 생략 중 어느 쪽을 사용할지
- Command마다 전용 Result DTO를 만들지 공통 Result를 사용할지
