# spring-gift-test

## 실행 방법

```bash
./gradlew bootRun        # 애플리케이션 실행
./gradlew test           # 전체 테스트 실행 (H2 인메모리 DB)
./gradlew cucumberTest   # Cucumber 테스트 실행 (PostgreSQL + Docker)
./gradlew build          # 빌드 + 테스트
```

## 사전 준비 (cucumberTest)

`./gradlew cucumberTest`를 실행하려면 Docker가 필요하다.

```bash
# Colima + Docker CLI 설치 (macOS)
brew install colima docker docker-compose

# Colima 시작
colima start

# 확인
docker compose version
```

## 테스트

### JUnit 인수 테스트

```bash
./gradlew test --tests "gift.CategoryAcceptanceTest"
./gradlew test --tests "gift.ProductAcceptanceTest"
./gradlew test --tests "gift.GiftAcceptanceTest"
```

### Cucumber BDD 테스트 (H2)

`src/test/resources/features/` 디렉토리의 한글 Gherkin 시나리오가 `./gradlew test` 실행 시 H2 인메모리 DB로 함께 실행된다.

### Cucumber BDD 테스트 (PostgreSQL)

```bash
./gradlew cucumberTest
```

Docker Compose로 PostgreSQL을 자동 기동하고, Cucumber 시나리오를 실행한 뒤, 컨테이너를 정리한다.

| 단계 | 태스크 | 동작 |
|------|--------|------|
| 1 | `dockerComposeUp` | PostgreSQL 컨테이너 기동 + 헬스체크 대기 |
| 2 | `cucumberTest` | `spring.profiles.active=cucumber`로 Cucumber 실행 |
| 3 | `dockerComposeDown` | 컨테이너 정리 (테스트 실패 시에도 실행) |

Cucumber HTML 리포트: `build/reports/cucumber/cucumber-report.html`

### Docker 기반 실행 (앱 컨테이너화)

애플리케이션을 Docker 컨테이너로 실행하여 프로덕션과 동일한 환경에서 테스트한다.

```bash
./gradlew dockerBuild        # Docker 이미지 빌드
./gradlew dockerUp           # PostgreSQL + App 컨테이너 기동
curl http://localhost:28080   # 애플리케이션 응답 확인
./gradlew cucumberTest       # Docker 환경에서 테스트
./gradlew dockerDown         # 컨테이너 정리
```

| 단계 | 태스크 | 동작 |
|------|--------|------|
| 1 | `dockerBuild` | Multi-stage build로 Docker 이미지 생성 (`spring-gift-test:latest`) |
| 2 | `dockerUp` | PostgreSQL + App 컨테이너 기동 + 헬스체크 대기 |
| 3 | `cucumberTest` | Docker 앱(28080)에 HTTP 요청, PostgreSQL에 직접 데이터 셋업 |
| 4 | `dockerDown` | 컨테이너 정리 (테스트 실패 시에도 실행) |