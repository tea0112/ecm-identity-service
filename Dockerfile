# Multi-stage Dockerfile for ECM Identity Service

# Build stage
FROM gradle:8.11.1-jdk21-alpine AS build

WORKDIR /app

# Copy gradle files for dependency caching
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./

# Copy gradle.properties if it exists
COPY gradle.properties* ./

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r ecmuser && useradd -r -g ecmuser ecmuser

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Create logs directory and set ownership
RUN mkdir -p logs && chown -R ecmuser:ecmuser /app

# Switch to non-root user
USER ecmuser

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
