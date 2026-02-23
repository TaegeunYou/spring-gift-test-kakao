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

`healthcheck`의 `pg_isready`로 PostgreSQL이 실제 접속 가능한 상태인지 확인한다. 컨테이너 기동 ≠ DB 접속 가능.

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

- `dependsOn dockerComposeUp`: 테스트 전에 PostgreSQL 기동
- `finalizedBy dockerComposeDown`: 테스트 성공/실패와 무관하게 항상 컨테이너 정리 (`try-finally`과 동일)

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

### 6단계: JPA 엔티티 PostgreSQL 호환 확인

엔티티에 `@Column(columnDefinition = ...)` 등에서 H2 전용 문법을 사용하는 부분이 있다면, 소스 코드를 확인하고 PostgreSQL 호환 문법으로 수정해라. 현재 프로젝트의 테이블은 PostgreSQL 예약어와 충돌하지 않는다.

### 7단계: 검증

```bash
./gradlew cucumberTest
./gradlew test
```

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

## 규칙

- Docker Compose 파일은 프로젝트 루트에 생성한다.
- 기존 `application.properties`와 `test` 태스크는 수정하지 않는다.
- `./gradlew cucumberTest`로 PostgreSQL 테스트, `./gradlew test`로 H2 테스트가 각각 독립 실행된다.
- 결과물은 `./gradlew cucumberTest`로 바로 실행 가능해야 한다.
- 엔티티에 PostgreSQL 비호환 문법이 있으면 확인 후 수정한다.
