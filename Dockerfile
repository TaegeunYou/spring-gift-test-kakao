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
