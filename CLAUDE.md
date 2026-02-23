# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 따르는 프로젝트 규칙을 정의한다.

## 빌드 및 실행

```bash
./gradlew build          # 빌드 + 테스트
./gradlew bootRun        # 애플리케이션 실행
./gradlew test           # 전체 테스트 실행
./gradlew test --tests "gift.SomeTest.methodName"  # 단일 테스트 실행
```

- Java 21, Spring Boot 3.5.8, Gradle 8.4
- 테스트: JUnit 5 (JUnit Platform)
- 데이터베이스: H2 인메모리 (runtime 의존성)

## 아키텍처

`gift` 루트 패키지 아래 레이어드 아키텍처:

- **ui** — REST 컨트롤러 (`@RestController`). 서비스에 위임하는 얇은 계층. `@RequestHeader("Member-Id")`로 사용자 식별
- **application** — 서비스 (`@Service`, 클래스 레벨 `@Transactional`)와 요청 DTO. DTO는 getter만 제공 (setter 없음, JSON 바인딩용 기본 생성자)
- **model** — JPA 엔티티, 값 객체, 리포지토리 인터페이스 (`JpaRepository<T, Long>`), 도메인 인터페이스 (예: `GiftDelivery`)
- **infrastructure** — 도메인 인터페이스 구현체 (예: `FakeGiftDelivery`), 카카오 API 설정용 `@ConfigurationProperties` 클래스

### 핵심 도메인 흐름

```
GiftRestController → GiftService → Option.decrease() + GiftDelivery.deliver()
```

`model`의 `GiftDelivery` 인터페이스를 `infrastructure`의 `FakeGiftDelivery`가 구현하여, 도메인이 외부 API와 분리된다.

## 코드 컨벤션

- 생성자 주입만 사용 — 필드 주입 금지, 파라미터에 `final`
- JPA 엔티티: `protected` 기본 생성자, `this(...)`로 위임하는 오버로드 생성자, `@GeneratedValue(strategy = IDENTITY)`
- Optional 처리: `.orElseThrow()` — 커스텀 예외 없이 사용
- `spring.jpa.open-in-view=false` — 지연 로딩은 서비스 계층에서 명시적으로 처리
- `@ConfigurationPropertiesScan` — 애플리케이션 클래스에 선언, 프로퍼티 클래스 자동 탐색
