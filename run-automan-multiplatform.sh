#!/bin/bash

echo "🚗 Automan Car Purchase System - Multi-Platform"
echo "=============================================="

# Check if Docker is running
if ! docker version >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi
echo "✅ Docker is running"

# Pull the multi-platform image
echo "📥 Pulling multi-platform image..."
docker pull automan-multiplatform:latest

if [ $? -ne 0 ]; then
    echo "❌ Failed to pull image. Make sure the image exists."
    exit 1
fi

# Stop any existing containers
echo "🛑 Stopping any existing containers..."
docker stop automan-multiplatform 2>/dev/null || true
docker rm automan-multiplatform 2>/dev/null || true

# Run the multi-platform container
echo "🚀 Starting Automan system..."
docker run -d \
    --name automan-multiplatform \
    -p 8080:8080 \
    -p 8083:8083 \
    -p 3306:3306 \
    automan-multiplatform:latest

if [ $? -eq 0 ]; then
    echo "✅ Automan system started successfully!"
    echo ""
    echo "🌐 Access Points:"
    echo "   • Frontend: http://localhost:8080"
    echo "   • Backend API: http://localhost:8083/api"
    echo "   • MySQL: localhost:3306"
    echo ""
    echo "🔑 Pre-configured Login:"
    echo "   • Email: admin@automan.com"
    echo "   • Password: admin123"
    echo ""
    echo "📊 Pre-populated Data:"
    echo "   • 1 Admin user"
    echo "   • 1 Client (Tokyo Auto Import)"
    echo "   • 4 Sample purchases"
    echo "   • Sample events and vessels"
    echo ""
    echo "🛠️ Management Commands:"
    echo "   • Stop: docker stop automan-multiplatform"
    echo "   • Restart: docker restart automan-multiplatform"
    echo "   • Logs: docker logs automan-multiplatform"
    echo "   • Remove: docker rm -f automan-multiplatform"
else
    echo "❌ Failed to start system"
    exit 1
fi
