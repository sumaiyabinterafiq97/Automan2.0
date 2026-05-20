#!/bin/bash

# Build and Deploy Frontend Script
# 1. Bumps frontend version in index.html (cache bust)
# 2. Builds Kotlin/JS production bundle
# 3. Rebuilds frontend Docker image
# 4. Recreates and starts the frontend container
#
# Usage: ./scripts/build-and-deploy-frontend.sh

set -e

echo "🚀 Frontend build and deploy (with new version)"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INDEX_HTML="$PROJECT_ROOT/src/jsMain/resources/index.html"

cd "$PROJECT_ROOT"

# Step 1: Bump version in index.html (cache bust)
# IMPORTANT: styles.css and automan-car-purchase.js versions can drift; always force BOTH to the same new version.
STYLES_VERSION=$(grep -oE 'styles\.css\?v=[0-9]+' "$INDEX_HTML" | head -1 | sed 's/.*v=//')
JS_VERSION=$(grep -oE 'automan-car-purchase\.js\?v=[0-9]+' "$INDEX_HTML" | head -1 | sed 's/.*v=//')
BASE_VERSION="${STYLES_VERSION:-0}"
if [[ -n "$JS_VERSION" && "$JS_VERSION" -gt "$BASE_VERSION" ]]; then
  BASE_VERSION="$JS_VERSION"
fi
NEW_VERSION=$((BASE_VERSION + 1))
echo "📌 Step 1: Bumping version v$BASE_VERSION → v$NEW_VERSION"
if [[ "$(uname)" == "Darwin" ]]; then
  sed -i '' -E "s/(styles\\.css\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
  sed -i '' -E "s/(automan-car-purchase\\.js\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
else
  sed -i -E "s/(styles\\.css\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
  sed -i -E "s/(automan-car-purchase\\.js\\?v=)[0-9]+/\\1$NEW_VERSION/g" "$INDEX_HTML"
fi
echo "   Updated index.html to v=$NEW_VERSION (forced for CSS + main JS)"
echo ""

# Step 2: Build frontend
echo "📦 Step 2: Building frontend (Gradle)..."
./gradlew jsBrowserProductionWebpack --no-daemon

if [ $? -ne 0 ]; then
    echo "❌ Frontend build failed!"
    exit 1
fi
echo "✅ Frontend build completed"
echo ""

# Step 3: Build Docker image
echo "🐳 Step 3: Building frontend Docker image..."
docker build -t automan20-frontend:latest -f docker/Dockerfile.frontend.prod .

if [ $? -ne 0 ]; then
    echo "❌ Docker build failed!"
    exit 1
fi
echo "✅ Docker image built"
echo ""

# Step 4: Recreate frontend container
echo "🔄 Step 4: Recreating frontend container..."

COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.multiplatform.yml"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-automan_local}"

# Do not use --no-deps: frontend depends_on backend (healthy). With --no-deps, nginx serves the SPA
# but /api/* returns 502 if MySQL/backend were never started (common after Docker Desktop restart).
compose_up_frontend() {
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" up -d --force-recreate frontend
}

if ! compose_up_frontend; then
    echo ""
    echo "⚠️  Compose up failed — often stale network after Docker Desktop restart or network prune:"
    echo "   (network ... not found)"
    echo "   Bringing the project down to drop stale refs, then starting frontend (+ mysql/backend deps) again..."
    echo ""
    # down does NOT remove mysql_data_v2 volumes; recreates bridge network automan_local_automan_network
    docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" down --remove-orphans || true
    if ! compose_up_frontend; then
        echo "❌ Container start failed after network recovery!"
        exit 1
    fi
fi
echo "✅ Frontend container started"
echo ""

# Verify
echo "🔍 Verifying..."
sleep 2
if docker ps --format '{{.Names}}' | grep -q 'automan_frontend_multiplatform'; then
    DEPLOYED=$(docker exec automan_frontend_multiplatform cat /usr/share/nginx/html/index.html 2>/dev/null | grep -oE 'v=[0-9]+' | head -1 || echo "v=?")
    echo "   Container: running"
    echo "   Deployed version: $DEPLOYED"
else
    echo "❌ Container not running!"
    exit 1
fi

echo ""
echo "✅ Done. Frontend: http://localhost:8080"
echo "   Hard refresh (Ctrl+Shift+R / Cmd+Shift+R) to load v=$NEW_VERSION"
echo ""
