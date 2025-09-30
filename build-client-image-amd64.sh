#!/bin/bash

# Build Client Docker Image for AMD64 Architecture
# This script builds a Docker image specifically for AMD64 (Intel/AMD) systems

echo "🔨 Building Automan Client Docker Image for AMD64..."

# Build the Docker image with AMD64 platform
docker build -f Dockerfile.client.amd64 -t automan-client:amd64 .

if [ $? -eq 0 ]; then
    echo "✅ Docker image built successfully!"
    echo "📦 Image: automan-client:amd64"
    echo "🏗️  Architecture: AMD64 (Intel/AMD compatible)"
    echo ""
    echo "🚀 To run the application:"
    echo "   docker run -p 8080:8080 automan-client:amd64"
    echo ""
    echo "📤 To save the image for client delivery:"
    echo "   docker save automan-client:amd64 | gzip > automan-client-amd64.tar.gz"
else
    echo "❌ Docker build failed!"
    exit 1
fi
