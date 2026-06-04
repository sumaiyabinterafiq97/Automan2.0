# JDK 17 builder image (override via: docker build --build-arg JAVA_BASE_IMAGE=...)
ARG JAVA_BASE_IMAGE=eclipse-temurin:17-jdk-jammy
FROM ${JAVA_BASE_IMAGE}

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy source code
COPY src src

# Make gradlew executable
RUN chmod +x gradlew

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Build the application (retry once on transient network failures to plugins.gradle.org / Maven)
RUN ./gradlew build -x test --no-daemon --stacktrace || \
    (echo "Gradle build failed, retrying with --refresh-dependencies..." && \
     ./gradlew build -x test --no-daemon --refresh-dependencies --stacktrace)

# Copy the built JAR file from the container's build directory
RUN ls -la build/libs/ && cp build/libs/automan-backend-1.0-SNAPSHOT.jar app.jar

# Expose port 8083
EXPOSE 8083

# Set environment variables (non-sensitive defaults - will be overridden by docker-compose)
ENV SPRING_PROFILES_ACTIVE=docker
# Note: Database credentials should be provided via docker-compose environment variables, not hardcoded here

# Health check — actuator (no auth); honor dynamic PORT on hosted platforms.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f "http://localhost:${PORT:-8083}/api/actuator/health" || exit 1

# Run the application with strict memory limits for Railway's 500MB container limit
CMD ["java", "-Xmx300m", "-Xss512k", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
