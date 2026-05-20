#!/usr/bin/env bash
# Rebuild backend image and restart backend container using Compose (keeps state in sync).
# Use this instead of: docker stop/rm + compose up (which can leave Compose with a stale container ID).
#
# If Docker Hub is unreachable ("lookup registry-1.docker.io: no such host"):
#   • Fix DNS: System Settings → Network → DNS servers e.g. 8.8.8.8, 1.1.1.1
#   • Docker Desktop → restart; try disabling VPN/firewall briefly
#   • Or mirror: export JAVA_BASE_IMAGE=your-registry/eclipse-temurin:17-jdk-jammy && run this script
#
# Faster / less to download inside Docker: ./rebuild-and-restart-backend.sh --prebuilt

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.multiplatform.yml"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-automan_local}"
BACKEND_DIR="$PROJECT_ROOT/backend"

DOCKERFILE="$BACKEND_DIR/Dockerfile"
JAVA_BASE_IMAGE="${JAVA_BASE_IMAGE:-eclipse-temurin:17-jdk-jammy}"

print_docker_hub_help() {
  echo ""
  echo "❌ Docker could not fetch the JDK base image from Docker Hub."
  echo "   Typical error: lookup registry-1.docker.io: no such host (DNS / offline)."
  echo ""
  echo "   Fix network/DNS first, then retry. Optional:"
  echo "   • JAVA_BASE_IMAGE=your.mirror/ed/jdk ./scripts/rebuild-and-restart-backend.sh"
  echo "   • Host build only (still needs ONE successful pull for the runtime layer): "
  echo "     ./scripts/rebuild-and-restart-backend.sh --prebuilt"
}

USE_PREBUILT=0
if [ "${1:-}" = "--prebuilt" ]; then
  USE_PREBUILT=1
  DOCKERFILE="$BACKEND_DIR/Dockerfile.prebuilt"
  JAVA_BASE_IMAGE="${JAVA_BASE_IMAGE:-eclipse-temurin:17-jre-jammy}"
fi

echo "🔨 Building backend image..."
echo "   Dockerfile: $DOCKERFILE"
echo "   Base image: $JAVA_BASE_IMAGE"

set +e
if [ "$USE_PREBUILT" = "1" ]; then
  echo "📦 Building JAR on host (Gradle)..."
  if ! (cd "$BACKEND_DIR" && ./gradlew build -x test --no-daemon -q); then
    echo "❌ Gradle build failed."
    exit 1
  fi
fi

docker build \
  --build-arg "JAVA_BASE_IMAGE=$JAVA_BASE_IMAGE" \
  -t automan20-backend:latest \
  -f "$DOCKERFILE" \
  "$BACKEND_DIR/"
BUILD_EXIT=$?
set -e

if [ "$BUILD_EXIT" -ne 0 ]; then
  print_docker_hub_help
  exit "$BUILD_EXIT"
fi

echo "🔄 Restarting backend..."
cd "$PROJECT_ROOT"
# If a backend container exists from a different Compose project, remove it
docker stop automan_backend_multiplatform 2>/dev/null || true
docker rm -f automan_backend_multiplatform 2>/dev/null || true
# Do not use --no-deps: backend depends on MySQL being healthy. Starting backend alone leaves DB down
# and causes 502 from nginx plus phpMyAdmin "mysql" host resolution failures.
docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" up -d --force-recreate backend

echo "✅ Done. Backend should be running on port 8083."
