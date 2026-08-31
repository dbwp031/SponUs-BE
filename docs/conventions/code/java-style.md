# Java 스타일

상태: Draft

## 목적

Java 코드의 표현 방식을 통일하여 리뷰 비용을 줄이고, 도메인 규칙이 기술적인 문법에 가려지지 않게 한다.

## 확정된 규칙

- 현재 시각은 주입된 `Clock`으로 생성한다.
- `LocalDateTime.now()`와 `Instant.now()`를 업무 코드에서 직접 호출하지 않는다.
- 컬렉션을 정상 결과로 반환할 때 `null` 대신 빈 컬렉션을 사용한다.
- JPA Entity를 Controller 응답으로 직접 반환하지 않는다.
- Domain 계층에서 Spring MVC 타입을 사용하지 않는다.

## 제안 규칙

### 의존성 주입

- 생성자 주입을 사용한다.
- 필드 주입을 사용하지 않는다.
- 생성자가 하나라면 `@Autowired`를 생략한다.

### 불변성

- 재할당하지 않는 지역 변수와 필드는 가능한 한 `final`로 표현한다.
- Request, Response, Command, Query DTO에는 Java `record` 사용을 우선 검토한다.
- Domain 객체는 setter 대신 의미 있는 행위 메서드로 상태를 변경한다.
- 외부에 컬렉션을 반환할 때 내부 mutable 컬렉션을 직접 노출하지 않는다.

### Optional과 null

- Repository 단건 조회 결과에는 `Optional`을 사용할 수 있다.
- 메서드 매개변수와 Entity 필드 타입으로 `Optional`을 사용하지 않는다.
- `Optional.get()`을 조건 확인 없이 호출하지 않는다.
- 선택 필드의 `null` 의미는 API와 DB 문서에 명시한다.

### 메서드

- 하나의 메서드는 하나의 추상화 수준을 유지한다.
- boolean 매개변수로 서로 다른 업무 흐름을 선택하지 않는다.
- 업무 상태 변경은 `changeStatus(value)`보다 `submit()`, `withdraw()`처럼 의도를 드러낸다.
- 검증과 상태 변경을 호출자에게 분산하지 않고 Aggregate 행위 안에서 함께 수행한다.

### 예외

- 정상적인 분기 제어를 위해 예외를 사용하지 않는다.
- 잡은 예외를 아무 처리 없이 무시하지 않는다.
- 인프라 예외를 그대로 사용자 메시지로 노출하지 않는다.

## 코드 예시

```java
public final class SubmitApplicationService {

    private final ApplicationRepository applicationRepository;
    private final Clock clock;

    public SubmitApplicationService(
        ApplicationRepository applicationRepository,
        Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }
}
```

```java
public record CreateApplicationCommand(
    Long announcementId,
    String eventScale,
    String promotionPlan,
    String motivation,
    Long attachmentAssetId
) {
}
```

## 금지 사항

- Field Injection
- 모든 Entity 필드에 대한 setter 생성
- Entity에 Lombok `@Data` 사용
- 의미 없는 `Util`, `Helper`, `Manager` 클래스 증가
- catch 후 빈 블록
- Domain 코드에서 현재 시각 직접 생성
- DTO를 여러 Bounded Context가 공동 소유

## 테스트 기준

- 정적 분석과 포매터는 CI에서 검사한다.
- Clock을 고정하여 시간 의존 테스트를 작성한다.
- 불변 컬렉션이 외부 코드에 의해 변경되지 않는지 검증한다.

## 미결정 사항

- Lombok 허용 범위
- Formatter와 Import 정렬 도구
- JavaDoc 작성 대상
- Checkstyle, Spotless, Error Prone 등 정적 분석 도구
- `record` 사용 범위와 Jackson/JPA 경계
