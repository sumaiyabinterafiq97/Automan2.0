#!/bin/bash

echo "🚀 Starting Automan in SMART DEVELOPMENT mode..."
echo "📝 Only rebuilds when code actually changes!"

# Check if we need to rebuild
NEED_REBUILD=false

# Check if frontend image exists
if ! docker image inspect automan20-frontend:latest >/dev/null 2>&1; then
    echo "🔨 Frontend image not found, need to build..."
    NEED_REBUILD=true
fi

# Check if source files are newer than the image
if [ "$NEED_REBUILD" = false ]; then
    SOURCE_TIME=$(find src -name "*.kt" -exec stat -f "%m" {} \; | sort -n | tail -1)
    IMAGE_TIME=$(docker image inspect automan20-frontend:latest --format='{{.Created}}' | xargs -I {} date -j -f "%Y-%m-%dT%H:%M:%S" "{}" "+%s" 2>/dev/null || echo "0")
    
    if [ "$SOURCE_TIME" -gt "$IMAGE_TIME" ]; then
        echo "🔨 Source files are newer than image, need to rebuild..."
        NEED_REBUILD=true
    fi
fi

# Rebuild if needed
if [ "$NEED_REBUILD" = true ]; then
    echo "🔨 Rebuilding frontend with latest changes..."
    docker-compose build --no-cache frontend
    echo "✅ Frontend rebuilt successfully!"
else
    echo "✅ Using existing frontend image (no rebuild needed)"
fi

# Start all services
echo "🐳 Starting all services..."
docker-compose up -d

echo ""
echo "✅ Smart development environment started!"
echo "🌐 Frontend: http://localhost:8080"
echo "🔧 Backend: http://localhost:8083"
echo "🗄️  Database: localhost:3307"
echo "📊 phpMyAdmin: http://localhost:8082"
echo ""
echo "💡 Next time you run this script:"
echo "   - If you haven't changed code: Starts instantly (no rebuild)"
echo "   - If you changed code: Rebuilds automatically"
echo ""
echo "🛑 To stop: docker-compose down"
