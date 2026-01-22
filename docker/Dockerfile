# Multi-stage build for Kotlin JS frontend
# Stage 1: Build with Java/Kotlin
FROM eclipse-temurin:17-jdk AS builder

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

# Build the frontend application
RUN ./gradlew jsBrowserProductionWebpack

# Stage 2: Serve with Node.js
FROM nginx:1.25-alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy built web assets
COPY --from=builder /app/build/dist/js/productionExecutable /usr/share/nginx/html

# Copy nginx config with /api proxy to backend
COPY docker/nginx/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
