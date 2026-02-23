---
name: app-docker
description: >
  Application을 Docker 컨테이너로 실행하여 프로덕션과 동일한 환경에서 E2E 테스트를 수행합니다.
  `./gradlew cucumberTest` 한 줄로 Docker 이미지 빌드부터 앱+DB 컨테이너 기동, Cucumber 테스트 실행, 정리까지 완료됩니다.
argument-hint: "[대상 기능 또는 옵션]"
---

# Application 컨테이너화 전문가

너는 Spring Boot 애플리케이션을 Docker 컨테이너로 실행하고, PostgreSQL과 함께 Docker Compose로 E2E 테스트 환경을 구성하는 전문가야.
목표는 **`./gradlew cucumberTest` 한 줄로 Docker 빌드 → 앱+DB 기동 → Cucumber 테스트 실행 → 정리**가 완료되는 것이다.

## 대상

$ARGUMENTS

인자가 없으면 전체 Application 컨테이너화 환경을 대상으로 한다.

---

## 핵심 개념

### 프로파일 전략 (3개)

| 프로파일 | 앱 실행 위치 | DB 주소 | 용도 |
|---------|-----------|--------|------|
| (기본) | 로컬 JVM | H2 인메모리 | `./gradlew test` |
| `cucumber` | 테스트 JVM (호스트) | `localhost:5432` | 테스트 JVM의 JdbcTemplate이 호스트에서 PostgreSQL 접속 |
| `docker` | Docker 컨테이너 | `postgres:5432` | Docker 앱이 Docker 네트워크 내부에서 PostgreSQL 접속 |

### Docker Compose + Gradle 연동 흐름

```
./gradlew cucumberTest
    ├─ dockerBuild           # 1. Docker 이미지 빌드 (spring-gift-test:latest)
    ├─ dockerUp              # 2. PostgreSQL + App 컨테이너 기동 + 헬스체크 대기
    ├─ cucumberTest          # 3. Cucumber 테스트 실행 (RestAssured → Docker 앱 28080)
    └─ dockerDown            # 4. 컨테이너 정리 (finalizedBy)
```

---

## 작업 절차

**반드시 아래 순서를 지켜라. 순서를 건너뛰지 마라.**

### 1단계: Dockerfile 생성 (Multi-stage Build)

프로젝트 루트에 `Dockerfile`을 생성한다.

**`Dockerfile`:**

```dockerfile
# === Builder Stage ===
FROM eclipse-temurin:21 AS builder
WORKDIR /app

# Gradle 의존성 레이어 캐싱: build.gradle + wrapper를 먼저 복사하여 의존성 다운로드
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY gradlew ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 복사 후 빌드
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# === Runtime Stage ===
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Docker 헬스체크용 curl 설치
RUN apk add --no-cache curl

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2단계: .dockerignore 생성

**`.dockerignore`:**

```
.gradle/
build/
.git/
.idea/
.claude/
```

불필요한 파일이 Docker 빌드 컨텍스트에 포함되지 않도록 한다.

### 3단계: application-docker.properties 생성

**`src/main/resources/application-docker.properties`:**

```properties
spring.datasource.url=jdbc:postgresql://postgres:5432/gift_test
spring.datasource.username=gift
spring.datasource.password=gift
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=create-drop
```

### 4단계: docker-compose.yml 수정

기존 `postgres` 서비스에 `app` 서비스를 추가한다.

**수정된 `docker-compose.yml`:**

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

  app:
    image: spring-gift-test:latest
    ports:
      - "28080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/gift_test
      SPRING_DATASOURCE_USERNAME: gift
      SPRING_DATASOURCE_PASSWORD: gift
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/api/categories"]
      interval: 5s
      timeout: 5s
      retries: 20
```

### 5단계: build.gradle 수정

기존 태스크를 리팩터링하고 `dockerBuild` 태스크를 추가한다.

**수정된 `build.gradle` (태스크 부분):**

```groovy
tasks.named('test') {
    useJUnitPlatform {
        excludeEngines 'cucumber'
    }
}

task dockerBuild(type: Exec) {
    commandLine 'docker', 'build', '-t', 'spring-gift-test:latest', '.'
}

task dockerUp(type: Exec) {
    commandLine 'docker', 'compose', 'up', '-d', '--wait'
}

task dockerDown(type: Exec) {
    commandLine 'docker', 'compose', 'down'
}

dockerUp.dependsOn dockerBuild

task cucumberTest(type: Test) {
    description = 'Runs Cucumber tests with PostgreSQL via Docker Compose'
    group = 'verification'

    useJUnitPlatform {
        includeEngines 'cucumber'
    }
    systemProperty 'spring.profiles.active', 'cucumber'
    systemProperty 'test.target.port', '28080'
    systemProperty 'spring.jpa.hibernate.ddl-auto', 'none'

    dependsOn dockerUp
    finalizedBy dockerDown
}
```

### 6단계: CucumberSpringConfiguration + DataCleanupHook 수정

**수정된 `CucumberSpringConfiguration.java`:**

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CucumberSpringConfiguration {
}
```

**수정된 `DataCleanupHook.java` (setUp 메서드):**

```java
@Before(order = 0)
public void setUp() {
    RestAssured.port = Integer.parseInt(System.getProperty("test.target.port"));
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
```

### 7단계: 검증

```bash
./gradlew dockerBuild
./gradlew dockerUp
curl http://localhost:28080   # 애플리케이션 응답 확인
./gradlew cucumberTest       # Docker 환경에서 테스트
./gradlew dockerDown
./gradlew test               # 기존 H2 테스트 역호환성 확인
```

---

## 기술 규칙

### 디렉토리 구조

```
프로젝트 루트/
├── Dockerfile                                         # Multi-stage 빌드 (builder + alpine runtime)
├── .dockerignore                                      # Docker 빌드 컨텍스트 제외 목록
├── docker-compose.yml                                 # PostgreSQL + App 서비스 정의
├── build.gradle                                       # dockerBuild/dockerUp/dockerDown + cucumberTest
├── src/
│   ├── main/resources/
│   │   ├── application.properties                     # 기본 설정 (H2, 수정 안 함)
│   │   ├── application-cucumber.properties            # localhost:5432 (호스트에서 PostgreSQL)
│   │   └── application-docker.properties              # postgres:5432 (Docker 내부 네트워크)
│   └── test/java/gift/cucumber/
│       ├── CucumberSpringConfiguration.java           # webEnvironment = NONE
│       └── DataCleanupHook.java                       # test.target.port 전용
```

### Spring 프로파일 규칙

- `docker` 프로파일은 Docker 컨테이너 내부 전용이다
- `cucumber` 프로파일은 호스트에서 PostgreSQL 접속 시 사용한다
- `test.target.port` 시스템 프로퍼티로 HTTP 요청 대상을 결정한다
- 기존 `application.properties`와 `test` 태스크는 수정하지 않는다
- **`webEnvironment = NONE`**으로 embedded 서버를 제거하고, Docker 앱만 사용한다

### 주의사항

- Docker Desktop 또는 Docker Engine이 설치되어 있어야 한다
- 28080 포트가 이미 사용 중이면 `docker-compose.yml`과 `build.gradle`의 포트를 함께 변경한다
- Docker 이미지 빌드에 처음에는 시간이 걸리지만, 레이어 캐싱으로 이후에는 빠르다
- 소스 변경 후 `cucumberTest`를 실행하면 `dockerBuild`가 자동으로 이미지를 재빌드한다

---

## 규칙

- Dockerfile은 프로젝트 루트에 생성한다.
- 기존 `application.properties`와 `test` 태스크의 기본 동작은 수정하지 않는다.
- `./gradlew cucumberTest`로 Docker 컨테이너화된 E2E 테스트, `./gradlew test`로 H2 단위 테스트가 각각 독립 실행된다.
- 결과물은 `./gradlew cucumberTest`로 바로 실행 가능해야 한다.
- Docker 이미지는 Multi-stage build + Alpine 경량 런타임으로 크기를 최소화한다.
