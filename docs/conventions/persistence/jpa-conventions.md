# JPA 컨벤션

상태: Draft

## 목적

JPA 매핑 편의 때문에 Aggregate 경계와 Bounded Context 경계가 무너지지 않도록 Entity, 연관관계, Repository 규칙을 정의한다.

## 모델링 원칙

- Aggregate Root를 통해 Aggregate 내부 상태를 변경한다.
- 다른 Bounded Context의 Entity와 JPA 연관관계를 만들지 않는다.
- 컨텍스트 밖의 대상은 `Long organizationId`, `Long assetId`처럼 ID로 참조한다.
- DB 외래 키와 애플리케이션 객체 참조 여부는 별도로 판단한다.
- 테이블 구조를 그대로 Domain 모델로 복사하지 않는다.

## Domain Model과 JPA

**제안:** 초기 v2는 구현 복잡도를 낮추기 위해 Aggregate에 JPA Annotation을 허용하는 실용적 DDD 방식을 사용한다. 단, Entity의 행위와 불변식은 기술 코드와 분리한다.

- `@Entity`, `@Id`, `@Version` 사용은 허용한다.
- Domain 메서드에서 `EntityManager`, QueryDSL, HTTP 타입을 사용하지 않는다.
- 영속 모델과 순수 Domain 모델을 분리해야 할 명확한 이유가 생기면 ADR로 결정한다.

## Entity 생성

**제안 규칙:**

- JPA 기본 생성자는 `protected`로 제한한다.
- 새 Entity는 생성자 또는 의미 있는 정적 팩토리로 만든다.
- 식별자와 감사 필드를 외부에서 setter로 주입하지 않는다.
- Entity에 Lombok `@Data`, 공개 setter, 공개 기본 생성자를 사용하지 않는다.

```java
@Entity
public class SponsorshipApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long lockVersion;

    protected SponsorshipApplication() {
    }

    public static SponsorshipApplication draft(
        Long clubOrganizationId,
        Long announcementId
    ) {
        return new SponsorshipApplication(clubOrganizationId, announcementId);
    }
}
```

## 연관관계

- 다른 컨텍스트는 ID로 참조한다.
- 같은 Aggregate 내부에서만 객체 연관관계를 우선 검토한다.
- `@ManyToOne`, `@OneToMany`는 기본적으로 LAZY를 사용한다.
- 양방향 연관관계는 두 방향 탐색이 실제 유스케이스에 필요할 때만 사용한다.
- `CascadeType.ALL`과 `orphanRemoval`은 생명주기가 Aggregate Root에 완전히 종속될 때만 사용한다.
- 대량 컬렉션을 Entity 연관관계로 항상 로딩하지 않는다.

## Repository

- Domain에는 Aggregate 단위 Repository 인터페이스를 둔다.
- Infrastructure에는 Spring Data JPA와 Query 구현을 둔다.
- Application Service가 Repository를 호출한다.
- Controller와 Domain Service가 Spring Data Repository를 직접 호출하지 않는다.
- 저장 단위는 Aggregate Root다.

```text
domain/repository/ApplicationRepository.java
infrastructure/persistence/ApplicationJpaRepository.java
infrastructure/persistence/JpaApplicationRepositoryAdapter.java
```

Spring Data 인터페이스와 Adapter를 항상 이중으로 둘지는 구현 복잡도를 확인한 후 확정한다.

## Query

- Command Repository와 화면 전용 Query를 구분할 수 있다.
- 복잡한 목록 조회는 Aggregate를 억지로 로딩하지 않고 Query 전용 Repository를 사용한다.
- Query 결과는 화면 전용 DTO로 투영할 수 있다.
- 다른 컨텍스트의 테이블을 임의 조인하여 경계를 우회하지 않는다.

## DB 제약조건

- Domain의 유일성 규칙은 DB Unique Constraint로도 보장한다.
- Constraint 이름을 명시하여 예외 변환과 운영 진단이 가능하게 한다.
- nullable, 길이, 외래 키 규칙을 애플리케이션 검증만으로 대체하지 않는다.

SponUs 필수 제약:

```text
UNIQUE (club_organization_id, announcement_id)
UNIQUE (origin_type, origin_id)
```

## 감사 필드

- 시스템 생성·수정 시각은 `Instant`로 저장한다.
- 감사 필드는 직접 `now()`를 호출하지 않고 공통 Clock 정책을 따른다.
- 작성자 Organization ID와 역사적 조직 Snapshot은 목적을 구분한다.

## 금지 사항

- Entity 직접 API 반환
- 다른 컨텍스트 Entity와 JPA 연관관계
- 기본 EAGER 컬렉션
- 모든 관계에 `CascadeType.ALL`
- 업무 규칙 없이 setter로 상태 변경
- Controller에서 Repository 직접 호출
- 마이그레이션 없이 Hibernate DDL 자동 변경을 운영에 적용

## 테스트 기준

- Repository 통합 테스트는 실제 DB 제약과 매핑을 검증한다.
- Unique Constraint 충돌을 검증한다.
- 낙관적 락 버전 증가를 검증한다.
- 필요한 Query 수와 N+1을 위험 기반으로 검증한다.

## 미결정 사항

- Flyway와 Liquibase 중 마이그레이션 도구
- 순수 Domain Model과 JPA Entity 분리 여부
- Spring Data Repository Adapter 이중화 범위
- 감사 필드 구현 방식
- 운영 환경 `ddl-auto` 설정
