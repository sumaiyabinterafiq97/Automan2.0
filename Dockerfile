# Multi-stage build for Kotlin JS frontend
# Stage 1: Build with Java/Kotlin
FROM openjdk:17-jdk-slim AS builder

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
FROM node:18-alpine

# Install necessary packages
RUN apk add --no-cache curl

# Set working directory
WORKDIR /app

# Install serve package globally
RUN npm install -g serve

# Copy the built web assets from the builder stage
COPY --from=builder /app/build/dist/js/productionExecutable /app/web

# Expose port 8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080 || exit 1

# Serve the built application
CMD ["serve", "-s", "/app/web", "-l", "8080"]
