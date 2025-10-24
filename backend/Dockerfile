# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy source code
COPY src src

# Make gradlew executable
RUN chmod +x gradlew

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Build the application
RUN ./gradlew build -x test

# Copy the built JAR file from the container's build directory
RUN ls -la build/libs/ && cp build/libs/automan-backend-1.0-SNAPSHOT.jar app.jar

# Expose port 8083
EXPOSE 8083

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/automan_car_purchase?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
ENV SPRING_DATASOURCE_USERNAME=automan_user
ENV SPRING_DATASOURCE_PASSWORD=automan_password

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1

# Run the application
CMD ["java", "-jar", "app.jar"]
