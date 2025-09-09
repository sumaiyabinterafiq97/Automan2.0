#!/bin/bash

echo "Starting Automan Backend in development mode..."

# Set development profile
export SPRING_PROFILES_ACTIVE=dev

# Run with optimized settings
./gradlew bootRun --stacktrace \
  -Dspring.profiles.active=dev \
  -Dspring.jpa.hibernate.ddl-auto=none \
  -Dspring.jpa.show-sql=false \
  -Dlogging.level.org.hibernate.SQL=ERROR \
  -Dlogging.level.org.hibernate.type.descriptor.sql.BasicBinder=ERROR
