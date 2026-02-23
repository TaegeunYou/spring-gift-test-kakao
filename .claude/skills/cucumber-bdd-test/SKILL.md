---
name: cucumber-bdd-test
description: >
  Cucumber를 사용한 BDD 인수 테스트를 설계하고 코드를 작성합니다.
  Gherkin 한글 시나리오(.feature)와 Step Definitions를 생성하여
  비개발자도 이해할 수 있는 테스트를 만듭니다.
argument-hint: "[기능명 또는 API 엔드포인트]"
---

# Cucumber BDD 인수 테스트 전문가

너는 Cucumber + Spring Boot 기반 BDD 인수 테스트 설계 전문가야.
목표는 **비개발자도 읽을 수 있는 한글 Gherkin 시나리오**로 사용자 관점의 행위를 검증하는 것이다.
내부 구현이 아니라 "관찰 가능한 결과" 기준으로 시나리오를 정의한다.

## 대상

$ARGUMENTS

인자가 없으면 전체 API 엔드포인트를 대상으로 한다.

---

## 핵심 개념

### Gherkin: 협업을 위한 도메인 언어

Gherkin은 **프로그래밍 언어가 아니라 도메인 언어**다:

- 비즈니스 행동을 표현한다
- **누구나 읽을 수 있어야 한다** — 기획자, QA, 비개발자 모두
- 구현이 바뀌어도 Gherkin은 유지되어야 한다
- Feature 파일만 읽으면 비즈니스 규칙을 이해할 수 있어야 한다

### Given / When / Then의 의미

| 한글 키워드 | 영어 | 의미 | 역할 |
|-------------|------|------|------|
| 조건 | Given | **초기 상태** | 시나리오의 전제조건을 설정한다. 시스템을 알려진 상태로 만든다 |
| 만일 | When | **사용자 행동** | 테스트 대상이 되는 핵심 행동을 수행한다. 시나리오당 하나가 이상적 |
| 그러면 | Then | **관찰 가능한 결과** | 행동의 결과를 검증한다. 반드시 관찰 가능한 출력이어야 한다 |
| 그리고 | And | 이전 키워드 계속 | Given/When/Then에 추가 조건을 덧붙인다 |
| 하지만 | But | 이전 키워드 계속 | 부정 조건을 강조할 때 사용 (Then 뒤에서 주로 사용) |

**Given은 `@Before` Hook과 다르다**: `@Before` Hook에서 일어나는 일은 Feature를 읽는 사람에게 보이지 않는다. 전제조건이 비즈니스 맥락에서 의미가 있다면 Given 스텝으로 명시해라.

### Gherkin 작성 3원칙

**1. 도메인 언어로 표현하라**

```gherkin
# ❌ 기술 용어 — 비개발자가 읽을 수 없음
만일 POST /api/gifts에 {"optionId": 1, "quantity": 1}을 요청하면
그러면 HTTP 응답 코드는 500이다

# ✅ 도메인 언어 — 누구나 이해 가능
만일 회원 1번이 "아이폰" 1개를 선물한다
그러면 재고 부족으로 실패한다
```

**2. 적절한 추상화 수준을 유지하라**

```gherkin
# ❌ 너무 구체적 — 재사용 불가
만일 회원 ID가 1인 사용자가 옵션 ID 42번에 수량 1로 POST /api/gifts를 호출하면

# ❌ 너무 일반적 — 읽을 수 없음
만일 작업을 수행하면

# ✅ 균형 잡힌 추상화
만일 회원 1번이 "아이폰" 1개를 선물한다
```

**3. 시나리오는 독립적이어야 한다**

각 시나리오는 다른 시나리오에 의존하지 않고 단독으로 실행 가능해야 한다. 시나리오 간 상태를 공유하지 마라 — 이는 테스트를 취약하게 만든다.

### 대표 시나리오 예시

```gherkin
# language: ko
기능: 선물하기

  시나리오: 재고가 1개인 옵션에 선물을 2번 시도
    조건 "아이폰 128GB" 옵션의 재고가 1개 있다
    만일 회원 1번이 "아이폰 128GB" 1개를 선물한다
    그러면 선물 발송이 성공한다
    만일 다시 선물을 시도한다
    그러면 재고 부족으로 실패한다
```

### 핵심 키워드 레퍼런스

| 키워드 | 역할 |
|--------|------|
| `io.cucumber:cucumber-spring` | Cucumber와 Spring TestContext를 통합하는 모듈. Spring의 `TestContextManager`를 내부적으로 사용 |
| `@CucumberContextConfiguration` | glue 경로의 설정 클래스에 선언. Cucumber가 Spring Boot 테스트 컨텍스트를 인식하게 한다. `@SpringBootTest`와 함께 사용 |
| `@ScenarioScope` | Spring Bean을 시나리오 단위 생명주기로 관리. 시나리오 시작 전 생성, 종료 후 폐기. 시나리오 간 상태 오염 방지 |
| `io.cucumber.java.ko.*` | 한글 Step Definition 어노테이션 패키지. `@조건`, `@만일`, `@그러면`, `@그리고`, `@하지만` 제공 |
| `src/test/resources/features/` | Feature 파일(.feature) 저장 위치. Cucumber가 이 경로에서 시나리오를 탐색 |
| JUnit Platform Suite API | `@Suite` + `@IncludeEngines("cucumber")`로 Cucumber 엔진을 JUnit Platform 위에서 실행 |

---

## Step Definitions: Gherkin과 코드의 연결

### Step Definition은 Adapter다

Step Definition의 역할은 **Gherkin 문장(비즈니스 언어)을 기술 구현으로 변환**하는 것이다:

```
Gherkin 문장 (비즈니스 언어)
    ↓ 패턴 매칭
Step Definition (어댑터)
    ↓ 실행
RestAssured / Repository (기술 구현)
```

Cucumber가 시나리오의 각 스텝을 만나면, 등록된 Step Definition 중 **표현식이 일치하는 메서드**를 찾아 실행한다.

### Cucumber Expressions로 파라미터 추출

Gherkin 스텝의 값을 Step Definition 메서드 파라미터로 자동 변환한다.

**Built-in 파라미터 타입:**

| 표현식 | 매칭 대상 | Java 타입 | 예시 |
|--------|-----------|-----------|------|
| `{string}` | `"쌍따옴표"` 또는 `'홑따옴표'` 안의 텍스트 | `String` | `"아이폰"` → `아이폰` |
| `{int}` | 정수 (예: `71`, `-19`) | `int` / `Integer` | `10` → `10` |
| `{long}` | 큰 정수 | `long` / `Long` | `1` → `1L` |
| `{float}` | 소수 (예: `3.6`, `.8`) | `float` / `Float` | `3.6` → `3.6f` |
| `{double}` | 소수 (64비트) | `double` / `Double` | `3.14` → `3.14d` |
| `{word}` | 공백 없는 단일 단어 | `String` | `banana` (O), `banana split` (X) |
| `{bigdecimal}` | 소수 → BigDecimal | `BigDecimal` | |
| `{biginteger}` | 정수 → BigInteger | `BigInteger` | |
| `{}` | 익명 — 아무 텍스트 매칭 (`/.*/`) | `String` | |

**추가 표현식 문법:**

- **선택적 텍스트**: `개(를)` → `개` 또는 `개를` 모두 매칭
- **대안 텍스트**: `성공/실패` → `성공` 또는 `실패` 매칭
- **이스케이프**: `\{`, `\}`, `\(`, `\)`, `\/` (Java에서는 `\\{` 등 이중 백슬래시)

**예시 — Gherkin과 Step Definition의 매핑:**

```gherkin
만일 회원 1번이 "아이폰 128GB" 3개를 선물한다
```

```java
@만일("회원 {long}번이 {string} {int}개를 선물한다")
public void 선물_보내기(Long fromId, String optionName, int qty) {
    // fromId = 1L, optionName = "아이폰 128GB", qty = 3
}
```

### ScenarioContext: Step 간 상태 공유

**문제:** Step Definition은 메서드 단위로 분리되어 있지만, Step 간 데이터 공유가 필요하다.

- Given에서 생성한 `optionId`를 When에서 사용
- When의 `Response`를 Then에서 검증

**해결:** `@ScenarioScope` Bean을 활용한 ScenarioContext 패턴

`@ScenarioScope`는 **시나리오 실행 전에 Bean을 생성하고, 시나리오 종료 후 폐기**한다. 따라서 시나리오마다 새로운 인스턴스가 보장되어 상태 오염이 없다.

```java
@Component
@ScenarioScope
public class ScenarioContext {
    private Response lastResponse;
    private final Map<String, Object> store = new HashMap<>();

    public Response getLastResponse() { return lastResponse; }
    public void setLastResponse(Response response) { this.lastResponse = response; }

    public void set(String key, Object value) { store.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) { return (T) store.get(key); }
}
```

```java
// ❌ Step Definition 인스턴스 필드 — Cucumber가 클래스를 재생성하지만 명시적이지 않음
private Long optionId;

// ✅ ScenarioContext — 시나리오 단위 격리가 Spring에 의해 보장됨
context.set("optionId", optionId);
Long optionId = context.get("optionId", Long.class);
```

**주의:** Cucumber-JVM은 시나리오마다 glue 클래스의 새 인스턴스를 생성하므로 인스턴스 필드로도 동작하지만, `@ScenarioScope` Bean을 사용하면 Spring DI를 통해 **여러 Step Definition 클래스 간에도 안전하게 상태를 공유**할 수 있다.

### Hook 실행 순서

| Hook | 실행 시점 | 용도 |
|------|-----------|------|
| `@BeforeAll` | 모든 시나리오 실행 전 **1회** | 전역 초기화 (DB 마이그레이션 등) |
| `@Before` | **각 시나리오의 첫 번째 스텝 실행 전** | 데이터 초기화, 상태 리셋 |
| `@BeforeStep` | 각 스텝 실행 전 | 스텝 단위 전처리 (거의 사용 안 함) |
| 스텝 실행 | Given → When → Then | |
| `@AfterStep` | 각 스텝 실행 후 | 스텝 단위 후처리 (거의 사용 안 함) |
| `@After` | **각 시나리오의 마지막 스텝 실행 후** (실패해도 실행) | 정리, 스크린샷 캡처 |
| `@AfterAll` | 모든 시나리오 실행 후 **1회** | 전역 정리 |

**실행 순서 제어:** `@Before(order = 10)` — 숫자가 낮을수록 먼저 실행. 같은 order면 선언 순서대로.

**태그 기반 조건 실행:** `@Before("@database")` — 특정 태그가 붙은 시나리오에서만 실행.

**핵심:** `@Before` Hook에서 일어나는 일은 Feature 파일을 읽는 사람에게 **보이지 않는다**. 데이터 초기화처럼 인프라적인 작업만 `@Before`에 넣고, 비즈니스 전제조건은 Given 스텝으로 명시해라.

### RestAssured 포트 설정

`@SpringBootTest(webEnvironment = RANDOM_PORT)`는 매 테스트 컨텍스트마다 랜덤 포트를 할당한다.

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {
    @LocalServerPort
    protected int port;  // Spring이 주입하는 실제 포트
}
```

Step Definition에서 `CucumberSpringConfiguration`을 상속하여 `port`에 접근하고, RestAssured 호출 전에 `RestAssured.port = port`를 설정한다. **이 설정을 누락하면 기본 포트(8080)로 요청하여 Connection Refused 에러가 발생**한다.

### 검증(Assertion) 라이브러리

Cucumber는 자체 검증 도구를 포함하지 않는다. Then 스텝에서 사용할 라이브러리를 선택한다:

- **Hamcrest** (RestAssured와 자연스럽게 통합): `response.then().body("name", is("교환권"))`
- **AssertJ**: `assertThat(actual).isEqualTo(expected)`
- **JUnit 5**: `assertEquals(expected, actual)`

이 프로젝트에서는 RestAssured의 `.then().statusCode()`, `.body()` 체이닝으로 검증한다.

---

## 프로젝트 컨텍스트

- 시스템 종류: Spring Boot 3.5.8 백엔드 REST API (Java 21, Gradle)
- DB: H2 인메모리 (`spring.jpa.open-in-view=false`)
- 테스트 프레임워크: Cucumber 7 + JUnit Platform + RestAssured + @SpringBootTest
- 외부 의존성: 카카오 API (현재 FakeGiftDelivery로 대체됨, GiftDelivery 인터페이스)
- 사용자 식별: `Member-Id` 요청 헤더 기반 (인증 시스템 없음)

### API 엔드포인트

| 메서드 | 경로 | 설명 | 요청 바디/헤더 |
|--------|------|------|----------------|
| POST | /api/categories | 카테고리 생성 | `{name}` |
| GET | /api/categories | 카테고리 목록 조회 | - |
| POST | /api/products | 상품 등록 | `{name, price, imageUrl, categoryId}` |
| GET | /api/products | 상품 목록 조회 | - |
| POST | /api/gifts | 선물하기 | `{optionId, quantity, receiverId, message}` + Header `Member-Id` |

### 도메인 엔티티 관계

```
Category 1──* Product 1──* Option
Member 1──* Wish *──1 Product
Gift(값 객체): from(Member) → to(Member), Option, quantity, message
```

### 핵심 비즈니스 규칙

- 선물하기 시 Option.decrease(quantity)로 재고 차감 → 재고 부족 시 IllegalStateException
- GiftDelivery.deliver(gift)로 외부 전송 (인터페이스 기반, 현재 Fake 구현)
- 상품은 반드시 카테고리에 속해야 함
- 옵션은 반드시 상품에 속해야 함
- 위시리스트는 회원+상품 조합

---

## 작업 절차

**반드시 아래 순서를 지켜라. 바로 코드를 작성하지 마라.**

### 1단계: 시나리오 도출

대상 기능에 대해 "사용자 여정(User Journey)" 기준으로 시나리오를 도출해라.

- 정상 흐름(Happy Path)과 실패 흐름(Edge Case) 모두 포함
- 각 시나리오는 **비즈니스 용어**로 작성 (기술 용어 금지)
- 관찰 가능한 결과를 명시
- **상태 변화는 실패 시나리오로 증명**: 부수효과(재고 차감 등)를 검증할 때 Repository를 조회하지 말고, 후속 API 호출의 성공/실패로 증명해라

### 2단계: Gherkin 시나리오 작성 (.feature 파일)

한글 Gherkin 키워드(`# language: ko`)를 사용하여 `.feature` 파일을 작성해라.

**한글 Gherkin 키워드 매핑:**

| 영어 | 한글 |
|------|------|
| Feature | 기능 |
| Scenario | 시나리오 |
| Scenario Outline | 시나리오 개요 |
| Examples | 예 |
| Given | 조건 |
| When | 만일 |
| Then | 그러면 |
| And | 그리고 |
| But | 하지만 |
| Background | 배경 |

**Feature 파일 위치:** `src/test/resources/features/{기능명}.feature`

**Feature 파일 예시:**

```gherkin
# language: ko
기능: 카테고리 관리
  사용자가 상품 카테고리를 생성하고 조회할 수 있다.

  시나리오: 유효한 이름으로 카테고리를 생성한다
    만일 "교환권" 이름으로 카테고리를 생성한다
    그러면 카테고리가 성공적으로 생성된다
    그리고 카테고리 이름이 "교환권"이다

  시나리오: 카테고리 생성 후 목록에서 조회된다
    조건 "교환권" 카테고리가 등록되어 있다
    만일 카테고리 목록을 조회한다
    그러면 목록에 "교환권" 카테고리가 포함되어 있다
```

### 3단계: 우선순위

리스크 기반으로 우선순위를 매겨라 (장애 임팩트/빈도/회귀 위험/핵심 가치).

### 4단계: 인프라 셋업

아래 기술 규칙을 따라 Cucumber + Spring Boot 통합에 필요한 인프라 코드를 작성해라.

### 5단계: Step Definitions 구현

Feature 파일의 각 스텝에 대응하는 Java Step Definition 코드를 작성해라.

---

## 기술 규칙

### Gradle 의존성

build.gradle에 아래 의존성을 추가한다:

```groovy
testImplementation 'io.cucumber:cucumber-java:7.20.1'
testImplementation 'io.cucumber:cucumber-spring:7.20.1'
testImplementation 'io.cucumber:cucumber-junit-platform-engine:7.20.1'
testImplementation 'org.junit.platform:junit-platform-suite'
testImplementation 'io.rest-assured:rest-assured'
```

**중요:** `io.rest-assured:rest-assured`가 이미 있으면 중복 추가하지 않는다.

### Cucumber 설정 파일

**`src/test/resources/junit-platform.properties`:**

```properties
cucumber.plugin=pretty,html:build/reports/cucumber/cucumber-report.html
cucumber.glue=gift.cucumber
cucumber.features=src/test/resources/features
cucumber.snippet-type=camelcase
```

| 설정 키 | 역할 |
|---------|------|
| `cucumber.plugin` | 리포터 플러그인. `pretty`(콘솔 출력), `html:`(HTML 리포트), `json:`(JSON), `junit:`(JUnit XML) |
| `cucumber.glue` | Step Definition, Hook이 위치한 Java 패키지 |
| `cucumber.features` | .feature 파일 탐색 경로 |
| `cucumber.snippet-type` | 미구현 스텝의 스니펫 스타일. `camelcase` 또는 `underscore` |

### Test Runner

**`@Suite` 클래스는 사용하지 않는다.** `cucumber-junit-platform-engine`이 classpath에 있으면 Gradle의 `useJUnitPlatform()`이 Cucumber 엔진을 자동 탐색하여 Feature 파일을 실행한다. `junit-platform.properties`의 설정만으로 충분하다.

`@Suite` + `@IncludeEngines("cucumber")` 클래스를 함께 사용하면 **시나리오가 2번 실행**되므로 주의한다.

### Spring 통합 설정

**`src/test/java/gift/cucumber/CucumberSpringConfiguration.java`:**

```java
package gift.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {

    @LocalServerPort
    protected int port;
}
```

`@CucumberContextConfiguration`은 **glue 경로 안에서 딱 하나**만 존재해야 한다. 이 클래스를 통해 Cucumber가 Spring의 `TestContextManager`를 사용하여 ApplicationContext를 초기화한다. Step Definition 클래스들은 이 클래스를 상속하여 `port`와 Spring Bean에 접근한다.

### 데이터 격리 (시나리오 간)

시나리오 간 데이터 격리를 위해 Cucumber `@Before` Hook을 사용한다.
**`@DirtiesContext`는 Cucumber에서 사용하지 않는다** — 시나리오 단위로 동작하지 않기 때문이다.
대신 `@Before` Hook에서 JdbcTemplate으로 테이블을 초기화한다.

**`src/test/java/gift/cucumber/DataCleanupHook.java`:**

```java
package gift.cucumber;

import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class DataCleanupHook {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before(order = 0)
    public void cleanUp() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE wish");
        jdbcTemplate.execute("TRUNCATE TABLE option");
        jdbcTemplate.execute("TRUNCATE TABLE product");
        jdbcTemplate.execute("TRUNCATE TABLE category");
        jdbcTemplate.execute("TRUNCATE TABLE member");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
}
```

- `order = 0`: 다른 `@Before` Hook보다 먼저 실행되어 깨끗한 상태를 보장
- **주의:** `@Before`는 반드시 `io.cucumber.java.Before`를 import (JUnit의 `@BeforeEach`가 아님)
- 새로운 테이블이 추가되면 TRUNCATE 목록에 포함해야 한다

### ScenarioContext

**`src/test/java/gift/cucumber/ScenarioContext.java`:**

```java
package gift.cucumber;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ScenarioScope
public class ScenarioContext {

    private Response lastResponse;
    private final Map<String, Object> store = new HashMap<>();

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response response) {
        this.lastResponse = response;
    }

    public void set(String key, Object value) {
        store.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) store.get(key);
    }
}
```

### Step Definitions 구현 패턴

**패키지:** `src/test/java/gift/cucumber/steps/`

**네이밍:** `{기능명}StepDefinitions.java` (예: `CategoryStepDefinitions.java`)

```java
package gift.cucumber.steps;

import gift.cucumber.CucumberSpringConfiguration;
import gift.cucumber.ScenarioContext;
import io.cucumber.java.ko.조건;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class GiftStepDefinitions extends CucumberSpringConfiguration {

    @Autowired
    private ScenarioContext context;

    @조건("{string} 옵션의 재고가 {int}개 있다")
    public void 옵션_재고_설정(String optionName, int quantity) {
        RestAssured.port = port;
        // Repository로 셋업 (Option은 컨트롤러 없음)
        // ... 셋업 코드 ...
        context.set("optionId", optionId);
    }

    @만일("회원 {long}번이 {string} {int}개를 선물한다")
    public void 선물_보내기(Long fromId, String optionName, int qty) {
        RestAssured.port = port;
        Long optionId = context.get("optionId", Long.class);
        Response response = given()
                .contentType(ContentType.JSON)
                .header("Member-Id", fromId)
                .body(Map.of(
                        "optionId", optionId,
                        "quantity", qty,
                        "receiverId", 2,
                        "message", "선물입니다"))
        .when()
                .post("/api/gifts")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @그러면("선물 발송이 성공한다")
    public void 선물_발송_성공() {
        context.getLastResponse().then().statusCode(200);
    }

    @그러면("재고 부족으로 실패한다")
    public void 재고_부족으로_실패() {
        context.getLastResponse().then().statusCode(500);
    }
}
```

### Step Definition 설계 원칙

**1. 재사용 가능하되 명확하게**

```java
// ✅ 재사용 가능하고 명확한 스텝
@만일("회원 {long}번이 {string} {int}개를 선물한다")

// ✅ 도메인 언어로 된 명확한 검증
@그러면("선물 발송이 성공한다")
@그러면("재고 부족으로 실패한다")
```

**2. 상태는 반드시 ScenarioContext로 관리**

```java
// ❌ 인스턴스 필드
private Long optionId;

// ✅ ScenarioContext — 여러 StepDefinition 클래스 간에도 공유 가능
context.set("optionId", optionId);
Long optionId = context.get("optionId", Long.class);
```

**3. 기술 세부사항은 Step Definition 안에 캡슐화**

Gherkin에는 도메인 언어만, HTTP/JSON/RestAssured 코드는 Step Definition 내부에만 존재한다.

### 공통 Step Definitions

여러 기능에서 재사용되는 스텝은 공통 클래스로 분리한다.

**`src/test/java/gift/cucumber/steps/CommonStepDefinitions.java`:**

```java
package gift.cucumber.steps;

import gift.cucumber.CucumberSpringConfiguration;
import gift.cucumber.ScenarioContext;
import io.cucumber.java.ko.그러면;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.*;

public class CommonStepDefinitions extends CucumberSpringConfiguration {

    @Autowired
    private ScenarioContext context;

    @그러면("요청이 성공한다")
    public void 요청이_성공한다() {
        context.getLastResponse().then().statusCode(200);
    }

    @그러면("요청이 실패한다")
    public void 요청이_실패한다() {
        context.getLastResponse().then().statusCode(anyOf(is(400), is(500)));
    }
}
```

### Feature 파일 작성 규칙

1. **파일 위치:** `src/test/resources/features/{기능명}.feature`
2. **언어 선언:** 파일 첫 줄에 `# language: ko`
3. **한 Feature 파일 = 한 기능 영역** (카테고리, 상품, 선물하기 등)
4. **배경(Background)으로 공통 전제조건 추출** — 단, 인프라 초기화가 아닌 비즈니스 전제조건만
5. **시나리오 개요(Scenario Outline)로 파라미터화 테스트 지원**
6. **도메인 언어 사용** — 기술 용어(HTTP, JSON, 상태코드) 금지

### 데이터 셋업 규칙

- 테스트 데이터는 **API 호출을 통해** 셋업한다 (Repository 직접 사용 금지)
  - 단, 컨트롤러가 없는 엔티티(Member, Option, Wish)는 Repository로 셋업한다
- 각 시나리오는 독립적으로 실행 가능해야 한다
- `@Before` Hook으로 시나리오 시작 전 DB를 초기화한다

### 검증 원칙 (API 경계 검증)

- **API 응답만으로 검증**한다 — Repository, Service 등 내부 컴포넌트를 직접 조회하지 않는다
- **상태 변화는 실패 시나리오로 증명**한다:
  - 좋은 예: 재고 1개 중 1개 선물 후, 추가 선물 → 실패 → 재고 차감 증명
  - 좋은 예: 카테고리 생성 후 목록 조회 → 목록에 포함 → 저장 증명
  - 나쁜 예: `optionRepository.findById(id)`로 재고 직접 확인

### Flake 방지

- 시나리오 간 데이터 격리 필수 (`@Before` Hook으로 DB 초기화)
- ID 하드코딩 금지 — 생성 응답에서 추출하여 ScenarioContext에 저장
- 순서 의존 테스트 금지
- `RestAssured.port` 설정 누락 주의

### 디렉토리 구조 요약

```
src/test/
├── java/gift/cucumber/
│   ├── CucumberSpringConfiguration.java  # Spring 통합 (@CucumberContextConfiguration)
│   ├── ScenarioContext.java              # Step 간 상태 공유 (@ScenarioScope)
│   ├── DataCleanupHook.java              # 데이터 격리 + 포트 설정 (@Before Hook)
│   └── steps/
│       ├── CategoryStepDefinitions.java  # 카테고리 스텝
│       ├── ProductStepDefinitions.java   # 상품 스텝
│       └── GiftStepDefinitions.java      # 선물하기 스텝
└── resources/
    ├── features/
    │   ├── category.feature              # 카테고리 시나리오
    │   ├── product.feature               # 상품 시나리오
    │   └── gift.feature                  # 선물하기 시나리오
    └── junit-platform.properties         # Cucumber 설정
```

---

## 검증

```bash
./gradlew test
```

- 모든 Cucumber 시나리오가 실행되고 통과해야 한다
- 기존 JUnit 테스트도 함께 통과해야 한다
- 빌드 리포트: `build/reports/cucumber/cucumber-report.html`

## 규칙

~~- 구현 세부(내부 함수 호출) 기반 설명은 최소화하고, 사용자/시스템 경계에서 관찰 가능한 결과로 말해라.
- 추측은 '가정' 섹션으로 분리해라. 확실한 근거가 없으면 단정하지 마라.
- 결과물은 `./gradlew test`로 바로 실행 가능한 코드여야 한다.~~
- 시나리오 도출 → Gherkin → 우선순위 → 인프라 셋업 → Step Definitions 순서를 반드시 지켜라.
- Feature 파일의 시나리오는 비개발자도 읽고 이해할 수 있어야 한다.
- Gherkin에는 도메인 언어만 사용하고, 기술 세부사항은 Step Definition에 캡슐화한다.

## 참고 자료

- [Cucumber 공식 문서](https://cucumber.io/docs/cucumber/)
- [Step Definitions](https://cucumber.io/docs/cucumber/step-definitions)
- [Cucumber API (Hooks)](https://cucumber.io/docs/cucumber/api)
- [Cucumber Expressions](https://github.com/cucumber/cucumber-expressions#readme)
- [State 관리](https://cucumber.io/docs/cucumber/state)
- [Assertions](https://cucumber.io/docs/cucumber/checking-assertions)
- [Configuration](https://cucumber.io/docs/cucumber/configuration)
- [Reporting](https://cucumber.io/docs/cucumber/reporting)
- [Mocking and Stubbing](https://cucumber.io/docs/cucumber/mocking-and-stubbing-with-cucumber)
- [Debugging](https://cucumber.io/docs/cucumber/debugging)
- [Environment Variables](https://cucumber.io/docs/cucumber/environment-variables)
- [cucumber-spring (GitHub)](https://github.com/cucumber/cucumber-jvm/tree/main/cucumber-spring)
