#!/bin/bash

# Build and Deploy Frontend Script
# This script rebuilds the frontend and restarts the Docker container

set -e  # Exit on error

echo "🚀 Starting frontend build and deployment process..."
echo ""

# Get the project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "📦 Step 1: Building frontend with Gradle..."
./gradlew clean jsBrowserProductionWebpack

if [ $? -ne 0 ]; then
    echo "❌ Frontend build failed!"
    exit 1
fi

echo "✅ Frontend build completed successfully"
echo ""

echo "🐳 Step 2: Building Docker image..."
docker build -f docker/Dockerfile -t automan20-frontend:latest .

if [ $? -ne 0 ]; then
    echo "❌ Docker build failed!"
    exit 1
fi

echo "✅ Docker image built successfully"
echo ""

echo "🔄 Step 3: Restarting frontend container..."
docker-compose -f docker/docker-compose.multiplatform.yml stop frontend
docker-compose -f docker/docker-compose.multiplatform.yml rm -f frontend
docker-compose -f docker/docker-compose.multiplatform.yml up -d frontend

if [ $? -ne 0 ]; then
    echo "❌ Container restart failed!"
    exit 1
fi

echo "✅ Frontend container restarted successfully"
echo ""

echo "🔍 Step 4: Verifying deployment..."
sleep 3

# Check if container is running
if docker ps | grep -q "automan_frontend_multiplatform"; then
    echo "✅ Frontend container is running"
    
    # Check the version in the container
    VERSION=$(docker exec automan_frontend_multiplatform cat /usr/share/nginx/html/index.html 2>/dev/null | grep -o 'automan-car-purchase.js?v=[0-9]*' | grep -o '[0-9]*' || echo "unknown")
    echo "📋 Deployed version: v=$VERSION"
    
    # Check file timestamp
    FILE_DATE=$(docker exec automan_frontend_multiplatform ls -lh /usr/share/nginx/html/automan-car-purchase.js 2>/dev/null | awk '{print $6, $7, $8}' || echo "unknown")
    echo "📅 File date: $FILE_DATE"
else
    echo "❌ Frontend container is not running!"
    exit 1
fi

echo ""
echo "✅ Build and deployment completed successfully!"
echo ""
echo "🌐 Frontend is available at: http://localhost:8080"
echo "💡 Remember to hard refresh your browser (Ctrl+F5 or Cmd+Shift+R) to clear cache"

