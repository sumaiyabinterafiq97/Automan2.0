#!/bin/bash

echo "🚀 Building Multi-Platform Automan Docker Image"
echo "=============================================="

# Set image name and tag
IMAGE_NAME="automan-multiplatform"
TAG="latest"

echo "📦 Building for multiple platforms..."
echo "   • linux/amd64 (Intel/AMD processors)"
echo "   • linux/arm64 (Apple Silicon)"

# Build multi-platform image
docker buildx build \
    --platform linux/amd64,linux/arm64 \
    --file Dockerfile.multiplatform \
    --tag ${IMAGE_NAME}:${TAG} \
    --push \
    .

if [ $? -eq 0 ]; then
    echo "✅ Multi-platform image built successfully!"
    echo "📋 Image: ${IMAGE_NAME}:${TAG}"
    echo "🌐 Platforms: linux/amd64, linux/arm64"
    echo ""
    echo "🎯 Usage:"
    echo "   docker run -p 8080:8080 -p 8083:8083 -p 3306:3306 ${IMAGE_NAME}:${TAG}"
    echo ""
    echo "🔗 Access Points:"
    echo "   • Frontend: http://localhost:8080"
    echo "   • Backend API: http://localhost:8083/api"
    echo "   • MySQL: localhost:3306"
    echo ""
    echo "🔑 Pre-configured Login:"
    echo "   • Email: admin@automan.com"
    echo "   • Password: admin123"
else
    echo "❌ Build failed!"
    exit 1
fi