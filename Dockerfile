FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/report-template-engine-1.0.0.jar app.jar
USER appuser
# Default port expected by Cloud Run. Spring Boot reads this via ${PORT:8101} in application.properties.
# Cloud Run overrides this at runtime with its own PORT env var (always 8080).
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
