# 인수 테스트 전략 (TEST_STRATEGY.md)

> 대상 시스템: spring-gift-test-kakao (Spring Boot 3.5.8, Java 21, H2 인메모리 DB)
> 작성 기준: `.claude/commands/acceptance-test/SKILL.md` 스킬 및 `CLAUDE.md` 프로젝트 규칙

---

## 1. 검증할 행위 목록

### 선택 기준

행위 선택은 **API 엔드포인트(시스템 경계)**를 기준으로 한다.
내부 클래스나 메서드가 아니라, 사용자가 HTTP 요청을 통해 관찰할 수 있는 결과를 기준으로 시나리오를 정의한다.

우선순위는 리스크 기반으로 다음 네 가지를 고려한다:

| 기준 | 설명 |
|------|------|
| **장애 임팩트** | 실패 시 사용자에게 미치는 영향의 크기 |
| **발생 빈도** | 해당 흐름이 실제로 실행되는 빈도 |
| **회귀 위험** | 코드 변경 시 깨질 가능성 |
| **핵심 가치** | 비즈니스 핵심 흐름인지 여부 |

### 행위 목록과 우선순위

#### P0 — 핵심 (장애 시 서비스 불가)

| # | 기능 | 행위 | 테스트 클래스 | 선택 이유 |
|---|------|------|--------------|-----------|
| 1 | 선물하기 | 유효한 요청으로 선물 전송 성공 | `GiftAcceptanceTest` | 시스템의 핵심 가치. 재고 차감 + 외부 전송이라는 두 가지 부수효과를 수반하는 가장 복잡한 흐름 |
| 2 | 선물하기 | 재고 전량 선물 후 추가 선물 실패 | `GiftAcceptanceTest` | 재고 차감이라는 상태 변화를 **실패 시나리오로 증명**하는 핵심 케이스 |
| 3 | 상품 등록 | 유효한 정보로 상품 등록 성공 | `ProductAcceptanceTest` | 선물하기의 전제 조건. 상품 없이는 옵션도 선물도 불가능 |
| 4 | 카테고리 생성 | 유효한 이름으로 카테고리 생성 성공 | `CategoryAcceptanceTest` | 상품 등록의 전제 조건. 데이터 의존 체인의 시작점 |

#### P1 — 중요 (비정상 입력 방어)

| # | 기능 | 행위 | 테스트 클래스 | 선택 이유 |
|---|------|------|--------------|-----------|
| 5 | 선물하기 | 존재하지 않는 옵션으로 선물 실패 | `GiftAcceptanceTest` | 잘못된 참조 데이터 방어. 실제 운영에서 발생 가능한 엣지 케이스 |
| 6 | 상품 조회 | 등록 후 목록 조회 시 포함 확인 | `ProductAcceptanceTest` | 저장 후 조회 일관성 검증 — 상품 데이터의 영속화 증명 |
| 7 | 카테고리 조회 | 생성 후 목록 조회 시 포함 확인 | `CategoryAcceptanceTest` | 저장 후 조회 일관성 검증 — 카테고리 데이터의 영속화 증명 |

#### P2 — 보조 (다건 처리, 경계값)

| # | 기능 | 행위 | 테스트 클래스 | 선택 이유 |
|---|------|------|--------------|-----------|
| 8 | 상품 조회 | 여러 상품 등록 후 모두 목록에 포함 | `ProductAcceptanceTest` | 다건 처리 시 누락 없이 모두 반환되는지 검증 |
| 9 | 카테고리 조회 | 여러 카테고리 생성 후 모두 목록에 포함 | `CategoryAcceptanceTest` | 다건 처리 시 누락 없이 모두 반환되는지 검증 |

### 의도적으로 제외한 행위

| 행위 | 제외 이유 |
|------|-----------|
| Option CRUD | 컨트롤러가 없어 API 경계에서 검증 불가. 인수 테스트의 범위 밖 |
| Wish CRUD | 컨트롤러가 없어 API 경계에서 검증 불가 |
| Member CRUD | 컨트롤러가 없음. Member-Id 헤더 기반 식별만 존재 |
| 카카오 API 실제 호출 | `FakeGiftDelivery`로 대체된 상태. 외부 시스템 연동은 인수 테스트 범위 밖 |
| 입력값 유효성 검증 (빈 이름, 음수 가격 등) | 현재 구현에 Validation 로직 없음. 존재하지 않는 행위를 테스트하지 않음 |

---

## 2. 테스트 데이터 전략

### 원칙: "API가 있으면 API로, 없으면 Repository로"

테스트 데이터 셋업 방식을 **컨트롤러 존재 여부**로 이분한다.

```
┌─────────────────────────────────────────────────────┐
│              데이터 셋업 의사결정 트리               │
│                                                     │
│  해당 엔티티에 REST 컨트롤러가 있는가?              │
│        │                                            │
│   ┌────┴────┐                                       │
│   Yes       No                                      │
│   │         │                                       │
│   API 호출  Repository @Autowired                   │
│                                                     │
│  Category ─── API (POST /api/categories)            │
│  Product  ─── API (POST /api/products)              │
│  Member   ─── Repository (MemberRepository)         │
│  Option   ─── Repository (OptionRepository)         │
│  Wish     ─── Repository (WishRepository)           │
└─────────────────────────────────────────────────────┘
```

#### API 호출 셋업 (Category, Product)

```java
// 카테고리 — form param 방식
Long categoryId = given()
        .param("name", "교환권")
    .when()
        .post("/api/categories")
    .then()
        .statusCode(200)
        .extract().response().jsonPath().getLong("id");

// 상품 — form param 방식
Long productId = given()
        .param("name", "아메리카노")
        .param("price", 5000)
        .param("imageUrl", "http://img.com/a.jpg")
        .param("categoryId", categoryId)
    .when()
        .post("/api/products")
    .then()
        .statusCode(200)
        .extract().response().jsonPath().getLong("id");
```

#### Repository 셋업 (Member, Option)

```java
@Autowired MemberRepository memberRepository;
@Autowired OptionRepository optionRepository;
@Autowired ProductRepository productRepository;

// Member — 컨트롤러 없음
Long senderId = memberRepository.save(new Member("보내는사람", "sender@test.com")).getId();

// Option — 컨트롤러 없음, Product 엔티티 참조 필요
var product = productRepository.findById(productId).orElseThrow();
Long optionId = optionRepository.save(new Option("옵션A", 10, product)).getId();
```

### ID 관리: 하드코딩 금지

- 모든 엔티티 ID는 **생성 응답에서 추출**하거나 **Repository 반환값에서 획득**
- `@GeneratedValue(strategy = IDENTITY)` 전략이므로 ID 값을 예측할 수 없음
- 나쁜 예: `optionId = 9999L` (존재하지 않는 리소스 테스트는 예외 — 의도적 불일치)

### 테스트 격리: `@DirtiesContext`

```java
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
```

| 전략 | 선택 여부 | 이유 |
|------|-----------|------|
| `@DirtiesContext(AFTER_EACH_TEST_METHOD)` | **채택** | 테스트마다 ApplicationContext 재생성. H2 인메모리 DB가 완전히 초기화되어 완벽한 격리 보장 |
| `@Transactional` | 미채택 | 테스트 트랜잭션이 실제 요청 트랜잭션과 분리됨. `RANDOM_PORT` 모드에서는 별도 스레드로 요청이 처리되어 롤백이 적용되지 않음 |
| 수동 cleanup (`@AfterEach`) | 미채택 | 삭제 순서 관리가 복잡하고 FK 제약 조건으로 실패 가능성 있음 |

### 헬퍼 메서드

반복되는 셋업 호출은 한국어 이름의 private 헬퍼 메서드로 추출한다.

```java
private Long 카테고리를_생성한다(String name) { ... }
private Long 상품을_등록한다(String name, int price, String imageUrl, Long categoryId) { ... }
private Long 옵션을_셋업한다(String name, int quantity) { ... }
private void 선물을_보낸다(Long senderId, Long optionId, int quantity, Long receiverId, String message) { ... }
```

- 각 헬퍼는 내부에서 `statusCode(200)` 검증 포함 — 셋업 실패 시 즉시 감지
- ID가 필요한 헬퍼는 `Long` 반환, 불필요하면 `void`
- 복합 셋업(옵션 = 카테고리 + 상품 + 옵션)은 단일 헬퍼로 캡슐화

---

## 3. 검증 전략

### 핵심 원칙: API 경계에서만 검증

```
┌──────────────────────────────────────────────────────────┐
│                    검증 경계                              │
│                                                          │
│  클라이언트  ──HTTP──▶  [API 경계]  ──▶  내부 구현       │
│             ◀──HTTP──  [여기서 검증]                      │
│                                                          │
│  검증 대상:  HTTP 상태 코드 + 응답 바디                  │
│  검증 금지:  Repository 조회, Service 메서드 호출 여부   │
└──────────────────────────────────────────────────────────┘
```

### 검증 유형별 전략

#### (1) 생성 검증 — "응답에 올바른 데이터가 반환되는가"

```java
given()
        .param("name", "교환권")
    .when()
        .post("/api/categories")
    .then()
        .statusCode(200)
        .body("id", notNullValue())    // 생성됨 증명
        .body("name", is("교환권"));   // 입력값 반영 증명
```

- 상태 코드 200 + 응답 바디의 필드 값으로 검증
- `id`가 `notNullValue()`면 영속화되었음을 간접 증명

#### (2) 영속화 검증 — "생성 후 조회 시 포함되는가"

```java
카테고리를_생성한다("교환권");

given()
    .when()
        .get("/api/categories")
    .then()
        .statusCode(200)
        .body("size()", is(1))
        .body("[0].name", is("교환권"));
```

- 생성 API → 조회 API 순서로 호출하여 **저장이 실제로 이루어졌음을 증명**
- Repository를 직접 조회하지 않고, GET API 응답으로 증명

#### (3) 상태 변화 검증 — "실패 시나리오로 부수효과를 증명"

이것이 이 전략의 가장 핵심적인 부분이다.

```java
// Given: 재고 10개 옵션
Long optionId = 옵션을_셋업한다("옵션A", 10);

// When: 10개 전량 선물
선물을_보낸다(senderId, optionId, 10, receiverId, "전량 선물");

// Then: 1개 추가 선물 시도 → 실패
given()
        .contentType(ContentType.JSON)
        .header("Member-Id", senderId)
        .body("""
            {"optionId": %d, "quantity": 1, "receiverId": %d, "message": "추가"}
            """.formatted(optionId, receiverId))
    .when()
        .post("/api/gifts")
    .then()
        .statusCode(500);  // ← 재고 차감이 일어났음을 증명
```

**추론 구조:**
1. 재고 10개 → 10개 선물 성공 (전제)
2. 이후 1개 추가 선물 시 500 응답 (관찰)
3. 따라서 재고가 0으로 차감되었음 (결론)

이 방식은 `optionRepository.findById(id).get().getQuantity() == 0`과 동일한 사실을 증명하지만, API 경계를 벗어나지 않는다.

#### (4) 실패 검증 — "잘못된 입력에 대한 방어"

```java
// 존재하지 않는 옵션 ID로 선물 시도
given()
        .contentType(ContentType.JSON)
        .header("Member-Id", senderId)
        .body("""
            {"optionId": 9999, "quantity": 1, "receiverId": %d, "message": "선물"}
            """.formatted(receiverId))
    .when()
        .post("/api/gifts")
    .then()
        .statusCode(500);
```

- 현재 시스템은 예외를 별도 처리하지 않으므로 500 응답
- "실패한다"는 사실 자체가 검증 대상

### 검증하지 않는 것

| 항목 | 이유 |
|------|------|
| 서비스 메서드 호출 횟수 | 내부 구현. 행위 기반 테스트에서 관심사가 아님 |
| Repository 직접 조회 | API 경계 바깥. 셋업에만 사용하고 검증에는 사용하지 않음 |
| `FakeGiftDelivery.deliver()` 호출 여부 | 인터페이스 뒤의 구현 상세. 추후 실제 카카오 API로 교체해도 테스트는 변경 없어야 함 |
| 로그 출력 | 부수효과이지만 관찰 가능한 API 응답이 아님 |

---

## 4. 주요 의사결정

### 결정 1: Repository를 셋업에 허용하되, 검증에는 금지

**문제:** Option, Member, Wish는 컨트롤러가 없다. 순수하게 API만으로는 테스트 데이터를 준비할 수 없다.

**선택지:**
| 방안 | 장점 | 단점 |
|------|------|------|
| (A) Repository 셋업 허용 | 현실적. 없는 API를 만들지 않아도 됨 | 테스트가 내부 구현(엔티티 생성자)에 약간 결합 |
| (B) 테스트용 API 추가 | 완전한 블랙박스 테스트 | 프로덕션 코드에 테스트 전용 코드 혼입 |
| (C) SQL 스크립트로 셋업 | API/엔티티 변경에 독립 | 스키마 변경 시 깨짐. 유지보수 부담 |

**결정: (A) — Repository 셋업 허용, 검증에는 사용 금지**

근거:
- "셋업"과 "검증"은 역할이 다르다. 셋업은 테스트의 전제 조건을 만드는 것이고, 검증은 시스템의 행위를 증명하는 것이다.
- Repository를 셋업에 사용하는 것은 "이 테스트가 시작되기 전에 DB에 이 데이터가 있다"는 전제 조건을 선언하는 것이지, 시스템의 행위를 검증하는 것이 아니다.
- 반면 검증에 Repository를 사용하면 API가 아닌 내부 구현에 결합되어, 구현 변경 시 테스트가 깨진다.

### 결정 2: 상태 변화를 실패 시나리오로 증명

**문제:** 선물하기의 핵심 부수효과인 "재고 차감"을 어떻게 검증하는가?

**선택지:**
| 방안 | 장점 | 단점 |
|------|------|------|
| (A) Repository로 재고 직접 확인 | 직관적. 숫자로 정확히 확인 가능 | API 경계 위반. 내부 구현 결합 |
| (B) 후속 API 호출의 실패로 증명 | API 경계 내에서 완결. 사용자 관점의 검증 | 간접 증명. 실패 원인이 재고 부족인지 100% 확신하려면 맥락 이해 필요 |

**결정: (B) — 실패 시나리오 증명**

근거:
- 인수 테스트의 목적은 "사용자가 관찰할 수 있는 결과"를 검증하는 것이다.
- 사용자에게 재고 차감은 "추가 선물이 안 된다"로 관찰된다. 이것이 사용자 관점의 진실이다.
- Repository 직접 조회는 개발자만 할 수 있는 행위이며, 인수 테스트의 관점이 아니다.
- 부수적 이점: `GiftDelivery` 구현이 `FakeGiftDelivery`에서 실제 카카오 API로 교체되어도 이 테스트는 변경 없이 동작한다.

### 결정 3: `@DirtiesContext` vs `@Transactional`

**문제:** 테스트 간 데이터 격리를 어떻게 보장하는가?

**선택지:**
| 방안 | 장점 | 단점 |
|------|------|------|
| (A) `@DirtiesContext(AFTER_EACH_TEST_METHOD)` | 완벽한 격리. 컨텍스트 재생성으로 DB 초기화 | 느림. 테스트마다 Spring Context를 새로 띄움 |
| (B) `@Transactional` | 빠름. 트랜잭션 롤백 | `RANDOM_PORT`에서 작동하지 않음. 테스트 스레드와 서버 스레드가 다른 트랜잭션 사용 |

**결정: (A) — `@DirtiesContext`**

근거:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`는 실제 서블릿 컨테이너를 띄운다. HTTP 요청은 별도 스레드에서 처리되므로 테스트 메서드의 `@Transactional` 롤백이 서버 측 데이터에 적용되지 않는다.
- 성능 비용이 있지만, 인수 테스트는 수량이 적고 정확성이 더 중요하다.
- H2 인메모리 DB이므로 컨텍스트 재생성 비용이 RDB 대비 낮다.

### 결정 4: 에러 응답 코드 500 허용

**문제:** 현재 시스템은 예외를 별도 처리하지 않아 모든 실패가 HTTP 500으로 반환된다. 테스트에서 400이나 404를 기대해야 하는가?

**결정: 현재 구현 그대로 500을 검증한다**

근거:
- 인수 테스트는 **현재 시스템의 행위**를 문서화하고 보호하는 것이 목적이다.
- 에러 핸들링이 추가되면(예: `@ExceptionHandler`로 400/404 반환) 그때 테스트도 함께 업데이트한다.
- 존재하지 않는 행위를 테스트하지 않는다.

### 결정 5: 카카오 API 연동은 테스트 범위에서 제외

**문제:** `GiftDelivery.deliver()` 호출이 실제로 메시지를 전송하는지 검증해야 하는가?

**결정: 제외한다. `FakeGiftDelivery`로 충분하다.**

근거:
- 현재 `GiftDelivery` 인터페이스는 도메인 계층에 있고, `FakeGiftDelivery`가 인프라 계층에서 구현한다. 이것은 의도된 설계다.
- 인수 테스트는 시스템 경계(HTTP API)에서 검증한다. 외부 시스템(카카오)과의 연동은 통합 테스트나 E2E 테스트의 영역이다.
- Fake 구현 덕분에 테스트가 외부 네트워크에 의존하지 않고, 빠르고 안정적으로 실행된다.

---

## 부록: 테스트 커버리지 매트릭스

| API 엔드포인트 | Happy Path | 영속화 검증 | 다건 처리 | 실패 케이스 | 상태 변화 증명 |
|---------------|:---:|:---:|:---:|:---:|:---:|
| POST /api/categories | O | O | O | - | - |
| GET /api/categories | - | O | O | - | - |
| POST /api/products | O | O | O | - | - |
| GET /api/products | - | O | O | - | - |
| POST /api/gifts | O | - | - | O (잘못된 옵션) | O (재고 소진) |

> `-` 표시는 해당 유형의 테스트가 없거나 해당 엔드포인트에 적용되지 않음을 의미한다.
