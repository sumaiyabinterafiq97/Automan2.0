#!/usr/bin/env bash
# Build linux/amd64 images and push to Docker Hub for AWS EC2 (Step 10 in AWS-FREE-TIER-DEPLOYMENT-GUIDE.md).
# Does NOT touch RDS data — only replaces container images.
#
# Usage:
#   ./scripts/update-aws-docker.sh              # backend + frontend
#   ./scripts/update-aws-docker.sh --backend    # backend only
#   ./scripts/update-aws-docker.sh --frontend   # frontend only
#   ./scripts/update-aws-docker.sh --prebuilt   # build JAR on host (recommended if Docker Gradle times out)
#
# After push, on EC2 (Session Manager):
#   cd ~/automan
#   sudo docker compose --env-file docker/.env -f docker/docker-compose.hub.yml pull
#   sudo docker compose --env-file docker/.env -f docker/docker-compose.hub.yml up -d

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
INDEX_HTML="$PROJECT_ROOT/src/jsMain/resources/index.html"

DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-sumaiya890}"
PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"

BUILD_BACKEND=1
BUILD_FRONTEND=1
USE_PREBUILT=0

for arg in "$@"; do
  case "$arg" in
    --backend) BUILD_FRONTEND=0 ;;
    --frontend) BUILD_BACKEND=0 ;;
    --prebuilt) USE_PREBUILT=1 ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

echo "🚀 AWS Docker Hub update (platform=$PLATFORM, user=$DOCKERHUB_USERNAME)"
echo ""

if [ "$BUILD_FRONTEND" = "1" ]; then
  echo "📌 Bumping frontend cache-bust version in index.html..."
  STYLES_VERSION=$(grep -oE 'styles\.css\?v=[0-9]+' "$INDEX_HTML" | head -1 | sed 's/.*v=//')
  JS_VERSION=$(grep -oE 'automan-car-purchase\.js\?v=[0-9]+' "$INDEX_HTML" | head -1 | sed 's/.*v=//')
  BASE_VERSION="${STYLES_VERSION:-0}"
  if [[ -n "${JS_VERSION:-}" && "$JS_VERSION" -gt "$BASE_VERSION" ]]; then
    BASE_VERSION="$JS_VERSION"
  fi
  NEW_VERSION=$((BASE_VERSION + 1))
  if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' -E "s/(styles\\.css\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
    sed -i '' -E "s/(automan-car-purchase\\.js\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
  else
    sed -i -E "s/(styles\\.css\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
    sed -i -E "s/(automan-car-purchase\\.js\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
  fi
  echo "   v$BASE_VERSION → v$NEW_VERSION"
  echo ""
  echo "📦 Building frontend (Gradle)..."
  (cd "$PROJECT_ROOT" && ./gradlew jsBrowserProductionWebpack --no-daemon -q)
  echo "🐳 Building frontend image..."
  docker build --platform "$PLATFORM" \
    --build-arg ENABLE_PROD_AUTO_LOGIN=true \
    -t "$DOCKERHUB_USERNAME/automan-frontend:latest" \
    -f "$PROJECT_ROOT/docker/Dockerfile.frontend.prod" \
    "$PROJECT_ROOT"
  echo "⬆️  Pushing frontend..."
  docker push "$DOCKERHUB_USERNAME/automan-frontend:latest"
  echo "✅ Frontend pushed"
  echo ""
fi

if [ "$BUILD_BACKEND" = "1" ]; then
  DOCKERFILE="$BACKEND_DIR/Dockerfile"
  JAVA_BASE="${JAVA_BASE_IMAGE:-eclipse-temurin:17-jdk-jammy}"
  if [ "$USE_PREBUILT" = "1" ]; then
    DOCKERFILE="$BACKEND_DIR/Dockerfile.prebuilt"
    JAVA_BASE="${JAVA_BASE_IMAGE:-eclipse-temurin:17-jre-jammy}"
    echo "📦 Building backend JAR on host (Gradle)..."
    (cd "$BACKEND_DIR" && ./gradlew build -x test --no-daemon -q)
  fi
  echo "🐳 Building backend image ($DOCKERFILE)..."
  docker build --platform "$PLATFORM" \
    --build-arg "JAVA_BASE_IMAGE=$JAVA_BASE" \
    -t "$DOCKERHUB_USERNAME/automan-backend:latest" \
    -f "$DOCKERFILE" \
    "$BACKEND_DIR"
  echo "⬆️  Pushing backend..."
  docker push "$DOCKERHUB_USERNAME/automan-backend:latest"
  echo "✅ Backend pushed"
  echo ""
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Images on Docker Hub. On EC2 (Session Manager), run:"
echo ""
echo "  cd ~/automan"
echo "  sudo docker compose --env-file docker/.env -f docker/docker-compose.hub.yml pull"
echo "  sudo docker compose --env-file docker/.env -f docker/docker-compose.hub.yml up -d"
echo "  sudo docker compose -f docker/docker-compose.hub.yml logs --tail=80 backend"
echo ""
echo "Verify: curl -s http://localhost:8083/api/purchases | head"
echo "Do NOT re-run database/*.sql on RDS — Flyway applies new migrations on backend start."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
