# spring-gift-test

## 실행 방법

```bash
./gradlew bootRun        # 애플리케이션 실행
./gradlew test           # 전체 테스트 실행 (JUnit + Cucumber)
./gradlew build          # 빌드 + 테스트
```

## 테스트

### JUnit 인수 테스트

```bash
./gradlew test --tests "gift.CategoryAcceptanceTest"
./gradlew test --tests "gift.ProductAcceptanceTest"
./gradlew test --tests "gift.GiftAcceptanceTest"
```

### Cucumber BDD 테스트

`src/test/resources/features/` 디렉토리의 한글 Gherkin 시나리오가 `./gradlew test` 실행 시 자동으로 함께 실행된다.

Cucumber HTML 리포트: `build/reports/cucumber/cucumber-report.html`