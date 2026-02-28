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

# Step 1: Bump version in index.html (styles.css and automan-car-purchase.js only)
CURRENT_VERSION=$(grep 'styles.css?v=' "$INDEX_HTML" | sed 's/.*v=\([0-9]*\).*/\1/' | head -1)
NEW_VERSION=$((CURRENT_VERSION + 1))
echo "📌 Step 1: Bumping version v$CURRENT_VERSION → v$NEW_VERSION"
if [[ "$(uname)" == "Darwin" ]]; then
  sed -i '' "s/styles.css?v=$CURRENT_VERSION/styles.css?v=$NEW_VERSION/g" "$INDEX_HTML"
  sed -i '' "s/automan-car-purchase.js?v=$CURRENT_VERSION/automan-car-purchase.js?v=$NEW_VERSION/g" "$INDEX_HTML"
else
  sed -i "s/styles.css?v=$CURRENT_VERSION/styles.css?v=$NEW_VERSION/g" "$INDEX_HTML"
  sed -i "s/automan-car-purchase.js?v=$CURRENT_VERSION/automan-car-purchase.js?v=$NEW_VERSION/g" "$INDEX_HTML"
fi
echo "   Updated index.html to v=$NEW_VERSION"
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

# Use compose so frontend joins same network as backend (nginx needs to resolve "backend" at startup)
COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.multiplatform.yml"
# Use -p docker to match backend's network (docker_automan_network)
docker stop automan_frontend_multiplatform 2>/dev/null || true
docker rm -f automan_frontend_multiplatform 2>/dev/null || true
docker compose -p docker -f "$COMPOSE_FILE" create frontend
docker start automan_frontend_multiplatform

if [ $? -ne 0 ]; then
    echo "❌ Container start failed!"
    exit 1
fi
echo "✅ Frontend container started"
echo ""

# Verify
echo "🔍 Verifying..."
sleep 2
if docker ps --format '{{.Names}}' | grep -q 'automan_frontend_multiplatform'; then
    DEPLOYED=$(docker exec automan_frontend_multiplatform cat /usr/share/nginx/html/index.html 2>/dev/null | grep -o 'v=[0-9]*' | head -1 || echo "v=?")
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
