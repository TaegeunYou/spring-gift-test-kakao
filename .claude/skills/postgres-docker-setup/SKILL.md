---
name: postgres-docker-setup
description: >
  H2 인메모리 DB를 PostgreSQL로 전환하고, Docker Compose로 테스트 환경을 자동화합니다.
  `./gradlew cucumberTest` 한 줄로 PostgreSQL 기동부터 Cucumber 테스트 실행까지 완료됩니다.
argument-hint: "[대상 기능 또는 옵션]"
---

# PostgreSQL + Docker Compose 통합 전문가

너는 Spring Boot 프로젝트에서 H2 인메모리 DB를 PostgreSQL로 전환하고, Docker Compose로 테스트 인프라를 자동화하는 전문가야.
목표는 **`./gradlew cucumberTest` 한 줄로 PostgreSQL 기동 → Cucumber 테스트 실행 → 정리**가 완료되는 것이다.

## 대상

$ARGUMENTS

인자가 없으면 전체 Cucumber 테스트 환경을 대상으로 한다.

---

## 핵심 개념

### 왜 PostgreSQL인가

- H2는 개발/단위 테스트용으로 편리하지만, PostgreSQL과 SQL 문법/동작 차이가 존재한다
- 프로덕션과 동일한 DB로 테스트해야 "테스트는 통과했는데 운영에서 실패" 문제를 예방할 수 있다
- Docker Compose를 사용하면 로컬 설치 없이 일관된 PostgreSQL 환경을 보장한다

### 프로파일 전략

| 프로파일 | DB | 용도 |
|----------|-----|------|
| (기본) | H2 인메모리 | 개발, 기존 JUnit 테스트 |
| `cucumber` | PostgreSQL (Docker) | Cucumber BDD 테스트 |

기존 JUnit 테스트(`*AcceptanceTest`)는 H2 그대로 유지하여 호환성을 보장한다.
Cucumber 테스트만 PostgreSQL을 사용한다.

### Docker Compose + Gradle 연동 흐름

```
./gradlew cucumberTest
    ├─ dockerComposeUp       # 1. PostgreSQL 컨테이너 기동 + 헬스체크 대기
    ├─ cucumberTest          # 2. spring.profiles.active=cucumber 으로 Cucumber 실행
    └─ dockerComposeDown     # 3. 컨테이너 정리 (finalizedBy)
```

### Test Isolation (시나리오 간 격리)

PostgreSQL에서는 H2의 `SET REFERENTIAL_INTEGRITY`를 사용할 수 없다.
대신 PostgreSQL 전용 방식으로 테이블을 초기화한다:

```sql
TRUNCATE TABLE wish, option, product, category, member CASCADE;
```

`CASCADE` 옵션이 외래키 의존관계를 자동 처리하므로, 테이블 순서를 신경 쓸 필요가 없다.

---

## 프로젝트 컨텍스트

- 시스템 종류: Spring Boot 3.5.8 백엔드 REST API (Java 21, Gradle)
- 현재 DB: H2 인메모리 (`runtimeOnly 'com.h2database:h2'`)
- 테스트 프레임워크: Cucumber 7 + JUnit Platform + RestAssured + @SpringBootTest
- 기존 테이블: `category`, `product`, `option`, `member`, `wish`
- 기존 테스트 격리: `DataCleanupHook`에서 JdbcTemplate TRUNCATE (H2 문법)
- 사용자 식별: `Member-Id` 요청 헤더 기반

### 도메인 엔티티 관계

```
Category 1──* Product 1──* Option
Member 1──* Wish *──1 Product
Gift(값 객체): from(Member) → to(Member), Option, quantity, message
```

---

## 작업 절차

**반드시 아래 순서를 지켜라. 순서를 건너뛰지 마라.**

### 1단계: Docker Compose 파일 생성

프로젝트 루트에 `docker-compose.yml`을 생성한다.

**`docker-compose.yml`:**

```yaml
services:
  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: gift_test
      POSTGRES_USER: gift
      POSTGRES_PASSWORD: gift
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U gift -d gift_test"]
      interval: 3s
      timeout: 3s
      retries: 10
```

**설정 설명:**

| 항목 | 값 | 이유 |
|------|-----|------|
| `image` | `postgres:16` | PostgreSQL 16 LTS |
| `ports` | `5432:5432` | Spring Boot가 localhost:5432로 접속 |
| `POSTGRES_DB` | `gift_test` | 테스트 전용 데이터베이스 |
| `POSTGRES_USER` | `gift` | 테스트 전용 사용자 |
| `POSTGRES_PASSWORD` | `gift` | 로컬 테스트용 간단한 비밀번호 |
| `healthcheck` | `pg_isready` | 컨테이너 기동 ≠ DB 접속 가능. 헬스체크로 실제 접속 가능 상태를 확인 |

### 2단계: Spring 프로파일 설정

**`src/main/resources/application-cucumber.properties`:**

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gift_test
spring.datasource.username=gift
spring.datasource.password=gift
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=create-drop
```

| 설정 | 역할 |
|------|------|
| `spring.datasource.url` | Docker Compose로 기동된 PostgreSQL 접속 URL |
| `driver-class-name` | PostgreSQL JDBC 드라이버 명시 |
| `database-platform` | Hibernate가 PostgreSQL SQL 문법을 생성 |
| `ddl-auto=create-drop` | 테스트 시작 시 스키마 생성, 종료 시 삭제. 매 실행마다 깨끗한 스키마 보장 |

**주의:** 기존 `application.properties`는 수정하지 않는다. H2 설정이 기본으로 유지되어 기존 JUnit 테스트가 영향받지 않는다.

### 3단계: Gradle 의존성 추가

`build.gradle`에 PostgreSQL JDBC 드라이버를 추가한다:

```groovy
dependencies {
    // 기존 의존성 유지
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'org.postgresql:postgresql'  // 추가
    // ...
}
```

**`runtimeOnly`를 사용하는 이유:** JDBC 드라이버는 런타임에만 필요하고, 컴파일 타임에 직접 참조하지 않는다.

### 4단계: Gradle cucumberTest 태스크 생성

`build.gradle`에 Docker Compose 연동 태스크를 추가한다:

```groovy
task dockerComposeUp(type: Exec) {
    commandLine 'docker', 'compose', 'up', '-d', '--wait'
}

task dockerComposeDown(type: Exec) {
    commandLine 'docker', 'compose', 'down'
}

task cucumberTest(type: Test) {
    description = 'Runs Cucumber tests with PostgreSQL via Docker Compose'
    group = 'verification'

    useJUnitPlatform {
        includeEngines 'cucumber'
    }
    systemProperty 'spring.profiles.active', 'cucumber'

    dependsOn dockerComposeUp
    finalizedBy dockerComposeDown
}
```

**태스크 설명:**

| 태스크 | 역할 |
|--------|------|
| `dockerComposeUp` | `docker compose up -d --wait` 실행. `-d`는 백그라운드, `--wait`는 헬스체크 통과까지 대기 |
| `dockerComposeDown` | `docker compose down` 실행. 컨테이너 정리 |
| `cucumberTest` | Cucumber 엔진만 실행 (`includeEngines 'cucumber'`). `spring.profiles.active=cucumber`으로 PostgreSQL 프로파일 활성화 |

**`dependsOn`과 `finalizedBy`:**
- `dependsOn dockerComposeUp`: 테스트 전에 PostgreSQL 기동
- `finalizedBy dockerComposeDown`: 테스트 성공/실패와 무관하게 항상 컨테이너 정리

**주의:** 기존 `test` 태스크는 수정하지 않는다. `./gradlew test`는 여전히 H2로 모든 테스트를 실행한다.

### 5단계: DataCleanupHook 수정 (PostgreSQL 호환)

기존 `DataCleanupHook`은 H2 전용 `SET REFERENTIAL_INTEGRITY` 문법을 사용한다.
PostgreSQL과 H2 모두 호환되도록 수정한다.

**수정된 `DataCleanupHook.java`:**

```java
package gift.cucumber;

import io.cucumber.java.Before;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

public class DataCleanupHook extends CucumberSpringConfiguration {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Before(order = 0)
    public void setUp() {
        RestAssured.port = port;
        if (isPostgresProfile()) {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE wish, option, product, category, member CASCADE");
        } else {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("TRUNCATE TABLE wish");
            jdbcTemplate.execute("TRUNCATE TABLE option");
            jdbcTemplate.execute("TRUNCATE TABLE product");
            jdbcTemplate.execute("TRUNCATE TABLE category");
            jdbcTemplate.execute("TRUNCATE TABLE member");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private boolean isPostgresProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("cucumber");
    }
}
```

**핵심:**
- `Environment`로 활성 프로파일을 확인하여 DB 종류에 맞는 SQL을 실행한다
- PostgreSQL: `TRUNCATE ... CASCADE`로 외래키 의존관계 자동 처리
- H2: 기존 `SET REFERENTIAL_INTEGRITY` 방식 유지
- **양쪽 프로파일 모두 호환**되므로, `./gradlew test`(H2)와 `./gradlew cucumberTest`(PostgreSQL) 모두 동작

### 6단계: JPA 엔티티 PostgreSQL 호환 확인

PostgreSQL로 전환 시 확인해야 할 JPA 엔티티 호환성 사항:

1. **`@GeneratedValue(strategy = IDENTITY)`** — PostgreSQL의 `GENERATED BY DEFAULT AS IDENTITY`에 매핑된다. 호환됨.
2. **예약어 충돌** — PostgreSQL에서 `user`, `order` 같은 예약어를 테이블/컬럼명으로 사용하면 에러 발생. 필요 시 `@Table(name = "\"user\"")` 또는 이름 변경.
3. **`@Column(columnDefinition = ...)`** — H2 전용 타입이 있으면 수정 필요.

현재 프로젝트의 테이블(`category`, `product`, `option`, `member`, `wish`)은 PostgreSQL 예약어와 충돌하지 않으므로 추가 수정이 필요 없다.

단, 엔티티에 `@Column` 등에서 H2 전용 문법을 사용하는 부분이 있다면, 소스 코드를 확인하고 PostgreSQL 호환 문법으로 수정해라.

### 7단계: 검증

```bash
./gradlew cucumberTest
```

**예상 실행 흐름:**
1. `dockerComposeUp` → Docker Compose로 PostgreSQL 컨테이너 기동
2. `--wait` → 헬스체크(`pg_isready`) 통과까지 대기 (보통 5~10초)
3. `cucumberTest` → `spring.profiles.active=cucumber`로 Cucumber 테스트 실행
4. Spring Boot가 PostgreSQL에 연결, `ddl-auto=create-drop`으로 스키마 생성
5. 각 시나리오 전 `DataCleanupHook`이 `TRUNCATE ... CASCADE`로 데이터 초기화
6. 모든 시나리오 실행 후 `dockerComposeDown` → 컨테이너 정리

**기존 테스트도 반드시 확인:**

```bash
./gradlew test
```

기존 JUnit 테스트(`*AcceptanceTest`)와 Cucumber 테스트가 H2에서 여전히 통과해야 한다.

---

## 기술 규칙

### 디렉토리 구조

```
프로젝트 루트/
├── docker-compose.yml                              # PostgreSQL 컨테이너 정의
├── build.gradle                                    # cucumberTest 태스크 + PostgreSQL 드라이버
├── src/
│   ├── main/resources/
│   │   ├── application.properties                  # 기본 설정 (H2, 수정 안 함)
│   │   └── application-cucumber.properties         # PostgreSQL 설정 (신규)
│   └── test/java/gift/cucumber/
│       └── DataCleanupHook.java                    # PostgreSQL/H2 호환 TRUNCATE (수정)
```

### Docker Compose 규칙

- `docker compose` (v2) 명령어를 사용한다 (`docker-compose` v1 아님)
- `--wait` 플래그로 헬스체크 통과까지 대기한다
- `healthcheck`에 `pg_isready`를 사용하여 PostgreSQL 접속 가능 상태를 확인한다
- 포트는 `5432:5432` (호스트:컨테이너) 기본값을 사용한다

### Spring 프로파일 규칙

- 프로파일 이름은 `cucumber`을 사용한다
- 프로파일 파일은 `application-cucumber.properties`
- `ddl-auto=create-drop`으로 테스트마다 깨끗한 스키마를 보장한다
- 기존 `application.properties`는 수정하지 않는다

### Gradle 태스크 규칙

- `cucumberTest`는 `test` 태스크와 독립적이다
- `includeEngines 'cucumber'`로 Cucumber 시나리오만 실행한다
- `systemProperty 'spring.profiles.active', 'cucumber'`로 프로파일을 활성화한다
- `dependsOn dockerComposeUp` + `finalizedBy dockerComposeDown`으로 라이프사이클을 관리한다

### 데이터 격리 규칙

- PostgreSQL에서는 `TRUNCATE ... CASCADE`를 사용한다
- H2에서는 기존 `SET REFERENTIAL_INTEGRITY` + 개별 TRUNCATE를 유지한다
- `Environment`로 활성 프로파일을 확인하여 분기한다
- 새로운 테이블이 추가되면 TRUNCATE 목록에 포함해야 한다

### 주의사항

- Docker Desktop 또는 Docker Engine이 설치되어 있어야 한다
- 5432 포트가 이미 사용 중이면 `docker-compose.yml`에서 호스트 포트를 변경하고, `application-cucumber.properties`의 URL도 함께 변경한다
- PostgreSQL 예약어(`user`, `order`, `group` 등)가 테이블/컬럼명에 사용되면 `@Table`/`@Column`에서 이스케이프 필요
- `gift` 테이블은 없다 (Gift는 값 객체). TRUNCATE 목록에 포함하지 않는다

---

## 핵심 키워드 레퍼런스

| 키워드 | 역할 |
|--------|------|
| `docker-compose.yml` | Docker Compose 서비스 정의 파일. `services` 블록 아래 컨테이너(PostgreSQL 등)의 이미지, 포트, 환경변수, 헬스체크를 선언한다 |
| `services` | `docker-compose.yml`의 최상위 키. 실행할 컨테이너를 정의한다. 각 서비스는 하나의 컨테이너에 대응 |
| `volumes` | 컨테이너 데이터를 호스트 파일시스템에 영속화한다. DB 데이터를 컨테이너 재시작 후에도 유지할 때 사용. 테스트용에서는 보통 생략 (매번 깨끗한 상태가 필요하므로) |
| `healthcheck` | 컨테이너의 **준비 상태**를 확인하는 설정. 컨테이너가 `running` 상태여도 내부 프로세스가 아직 요청을 받을 수 없을 수 있다. `healthcheck`가 통과해야 `healthy` 상태가 된다 |
| `pg_isready` | PostgreSQL 내장 유틸리티. DB 서버가 TCP 연결을 수락할 준비가 되었는지 확인한다. `healthcheck`의 `test` 명령으로 사용 |
| `@ActiveProfiles("cucumber")` | Spring 테스트에서 특정 프로파일을 활성화하는 어노테이션. `application-cucumber.properties`를 로드한다. 이 프로젝트에서는 Gradle `systemProperty`로 프로파일을 활성화하므로 어노테이션 대신 사용 |
| `application-cucumber.properties` | Spring Boot의 프로파일 기반 설정 파일. `cucumber` 프로파일이 활성화되면 `application.properties` 위에 덮어써진다. PostgreSQL 접속 정보를 여기에 정의 |
| `Exec` (Gradle task type) | 외부 프로세스(shell 명령)를 실행하는 Gradle 태스크 타입. `commandLine`으로 실행할 명령을 지정한다. `docker compose up`, `docker compose down` 등을 실행할 때 사용 |
| `doFirst` | Gradle 태스크 실행 **직전**에 수행할 액션을 추가한다. 태스크의 메인 액션 전에 전처리(로그 출력, 상태 확인 등)를 넣을 때 사용 |
| `finalizedBy` | Gradle 태스크 종료 후 **항상** 실행할 후속 태스크를 지정한다. 메인 태스크가 성공하든 실패하든 반드시 실행된다. `try-finally`와 같은 역할 — DB 컨테이너 정리에 핵심 |
| `dependsOn` | Gradle 태스크 실행 **전에** 선행 태스크를 실행한다. `cucumberTest.dependsOn(dockerComposeUp)`은 PostgreSQL이 준비된 후에만 테스트를 시작하도록 보장 |
| `includeEngines 'cucumber'` | JUnit Platform에서 Cucumber 엔진만 선택적으로 실행한다. 기존 JUnit 테스트를 제외하고 Cucumber 시나리오만 실행할 때 사용 |
| `systemProperty` | Gradle Test 태스크에서 JVM 시스템 프로퍼티를 설정한다. `spring.profiles.active`를 이 방식으로 전달하면 코드 수정 없이 프로파일을 전환할 수 있다 |

---

## 탐구 질문

### Docker Compose의 services, volumes는 무엇인가?

**services:** 실행할 컨테이너 목록을 정의한다. 각 서비스는 Docker 이미지, 포트 매핑, 환경변수, 헬스체크 등을 포함한다.

```yaml
services:
  postgres:              # 서비스 이름 (컨테이너 이름으로도 사용)
    image: postgres:16   # 사용할 Docker 이미지
    ports:
      - "5432:5432"      # 호스트포트:컨테이너포트
    environment:         # 컨테이너 내부 환경변수
      POSTGRES_DB: gift_test
```

**volumes:** 컨테이너의 파일시스템 경로를 호스트에 매핑하여 데이터를 영속화한다.

```yaml
services:
  postgres:
    volumes:
      - pgdata:/var/lib/postgresql/data   # Named volume → 컨테이너 재시작 후에도 데이터 유지

volumes:
  pgdata:   # Named volume 선언
```

이 프로젝트에서는 **volumes를 사용하지 않는다**. 테스트 환경에서는 매번 깨끗한 DB가 필요하므로, 컨테이너를 내리면 데이터가 함께 사라지는 것이 오히려 바람직하다.

### Health check는 왜 필요한가?

컨테이너가 `running` 상태 = DB가 요청을 받을 수 있는 상태는 **아니다**.

```
시간축: ────────────────────────────────────────────→
컨테이너:  [시작] ──── [running] ──── [running] ────
PostgreSQL: [시작] ── [초기화 중...] ── [ready!] ────
                                        ↑
                      healthcheck 통과 시점 (이때부터 접속 가능)
```

`docker compose up -d`만 사용하면 컨테이너가 `running`이 되자마자 다음 단계로 넘어가서, PostgreSQL이 아직 초기화 중일 때 Spring Boot가 접속을 시도하여 `Connection refused` 에러가 발생한다.

`docker compose up -d --wait`를 사용하면 `healthcheck`가 통과(`healthy` 상태)할 때까지 기다린다. `pg_isready`는 PostgreSQL이 실제로 TCP 연결을 수락할 준비가 되었는지 확인하므로, 접속 가능 시점을 정확히 판단할 수 있다.

### Spring Profile은 어떻게 동작하는가?

Spring Boot는 `application.properties`를 기본으로 로드한 뒤, 활성화된 프로파일의 설정 파일을 **덮어쓴다**.

```
1. application.properties 로드        (기본 설정)
2. application-{profile}.properties 로드  (프로파일 설정 — 겹치는 키만 덮어씀)
```

프로파일 활성화 방법:

| 방법 | 사용 위치 | 예시 |
|------|-----------|------|
| JVM 시스템 프로퍼티 | Gradle, 커맨드라인 | `-Dspring.profiles.active=cucumber` |
| `@ActiveProfiles` | 테스트 클래스 | `@ActiveProfiles("cucumber")` |
| 환경변수 | CI/CD, 운영 | `SPRING_PROFILES_ACTIVE=cucumber` |

이 프로젝트에서는 **Gradle `systemProperty`**를 사용한다. 이렇게 하면 테스트 코드에 `@ActiveProfiles`를 추가하지 않아도 되어, 같은 코드가 H2(`./gradlew test`)와 PostgreSQL(`./gradlew cucumberTest`) 양쪽에서 동작한다.

### Gradle Task에서 Shell 스크립트를 어떻게 실행하는가?

Gradle의 `Exec` 태스크 타입을 사용한다:

```groovy
// 기본: commandLine으로 명령어 + 인자를 리스트로 전달
task dockerComposeUp(type: Exec) {
    commandLine 'docker', 'compose', 'up', '-d', '--wait'
}

// doFirst: 태스크 실행 직전에 추가 액션 수행
task dockerComposeUp(type: Exec) {
    doFirst {
        println 'Starting PostgreSQL...'
    }
    commandLine 'docker', 'compose', 'up', '-d', '--wait'
}

// workingDir: 명령어 실행 디렉토리 지정 (기본: 프로젝트 루트)
task dockerComposeUp(type: Exec) {
    workingDir projectDir
    commandLine 'docker', 'compose', 'up', '-d', '--wait'
}
```

`Exec`는 프로세스를 직접 실행하므로 셸 해석(`&&`, `|`, `>` 등)은 지원하지 않는다. 파이프라인이 필요하면 `commandLine 'sh', '-c', 'cmd1 && cmd2'`를 사용한다.

### 테스트 실패 시에도 DB를 정리하려면 어떻게 해야 하는가?

Gradle의 `finalizedBy`를 사용한다:

```groovy
task cucumberTest(type: Test) {
    dependsOn dockerComposeUp       // 선행: PostgreSQL 기동
    finalizedBy dockerComposeDown   // 후행: 항상 실행 (성공/실패 무관)
}
```

`finalizedBy`는 Java의 `try-finally`와 같은 역할이다:

```
try {
    dockerComposeUp     // dependsOn
    cucumberTest        // 메인 태스크
} finally {
    dockerComposeDown   // finalizedBy — 테스트가 실패해도 반드시 실행
}
```

이 방식이 없으면 테스트 실패 시 PostgreSQL 컨테이너가 계속 떠 있어 포트 충돌, 리소스 낭비 문제가 발생한다.

### H2 단위 테스트와 PostgreSQL 통합 테스트를 어떻게 분리하는가?

**두 개의 독립적인 Gradle 태스크**로 분리한다:

```
./gradlew test           → 기존 test 태스크
                           - JUnit + Cucumber 모든 테스트 실행
                           - H2 인메모리 DB (기본 프로파일)
                           - Docker 불필요

./gradlew cucumberTest   → 새로운 cucumberTest 태스크
                           - Cucumber 시나리오만 실행 (includeEngines 'cucumber')
                           - PostgreSQL (cucumber 프로파일)
                           - Docker Compose 자동 기동/정리
```

같은 Cucumber 테스트 코드가 양쪽에서 동작한다. 차이는 **어떤 프로파일이 활성화되느냐**뿐이다:
- `test`: 프로파일 미지정 → `application.properties` → H2
- `cucumberTest`: `spring.profiles.active=cucumber` → `application-cucumber.properties` → PostgreSQL

`DataCleanupHook`이 활성 프로파일에 따라 SQL 문법을 자동 분기하므로, 테스트 코드 수정 없이 양쪽 DB에서 동작한다.

---

## 네트워크 이해

### 테스트 코드는 어디서 실행되는가?

```
┌─────────────────────────────── Host (개발자 Mac/Linux) ──────────────────────────────┐
│                                                                                       │
│  ┌─────────────────────────────────┐    ┌──────────────────────────────────────────┐  │
│  │ JVM (Gradle → Spring Boot)      │    │ Docker Container (postgres:16)           │  │
│  │                                 │    │                                          │  │
│  │  CucumberTest                   │    │  PostgreSQL Server                       │  │
│  │    → Spring Boot 기동            │    │    - DB: gift_test                       │  │
│  │    → DataSource 연결 ──────────────────→ 0.0.0.0:5432                           │  │
│  │    → RestAssured → localhost:port│    │                                          │  │
│  │    → DataCleanupHook            │    │                                          │  │
│  │                                 │    │                                          │  │
│  └─────────────────────────────────┘    └──────────────────────────────────────────┘  │
│         ↑                                         ↑                                   │
│    호스트에서 실행                           Docker 컨테이너에서 실행                    │
│                                                                                       │
│  포트 매핑: localhost:5432 ──→ 컨테이너:5432                                          │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

- **테스트 코드(JVM)**: 호스트에서 실행된다. `./gradlew cucumberTest`가 호스트의 JVM에서 Spring Boot를 기동하고, Cucumber 시나리오를 실행한다.
- **PostgreSQL**: Docker 컨테이너 안에서 실행된다. 호스트와는 격리된 네트워크/파일시스템을 가진다.
- **연결 경로**: Spring Boot(호스트) → `localhost:5432` → Docker 포트 매핑 → 컨테이너 내부 PostgreSQL

### PostgreSQL은 어떤 주소로 접근하는가?

`localhost:5432`로 접근한다.

```yaml
# docker-compose.yml
ports:
  - "5432:5432"    # 호스트의 5432 포트를 컨테이너의 5432 포트에 매핑
```

```properties
# application-cucumber.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gift_test
```

**왜 `localhost`인가?** `docker compose`의 `ports` 매핑이 호스트의 `localhost:5432`를 컨테이너의 `5432` 포트로 포워딩하기 때문이다. 테스트 코드는 호스트에서 실행되므로 `localhost`로 접근하면 Docker가 자동으로 컨테이너로 라우팅한다.

**주의:** 컨테이너 간 통신에서는 `localhost`가 아닌 서비스 이름(`postgres`)을 사용한다. 하지만 이 프로젝트에서는 테스트 코드가 호스트에서 실행되므로 `localhost`가 올바르다.

---

## 규칙

- Docker Compose 파일은 프로젝트 루트에 생성한다.
- 기존 `application.properties`와 `test` 태스크는 수정하지 않는다.
- `./gradlew cucumberTest`로 PostgreSQL 테스트, `./gradlew test`로 H2 테스트가 각각 독립 실행된다.
- 결과물은 `./gradlew cucumberTest`로 바로 실행 가능해야 한다.
- 엔티티에 PostgreSQL 비호환 문법이 있으면 확인 후 수정한다.

## 참고 자료

- [Docker Compose 공식 문서](https://docs.docker.com/compose/)
- [Spring Boot Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)
- [Gradle Exec Task](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.Exec.html)
