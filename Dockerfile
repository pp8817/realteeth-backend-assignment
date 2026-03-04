FROM gradle:8.14.4-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
