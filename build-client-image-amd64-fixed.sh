#!/bin/bash

# Build Client Docker Image for AMD64 Architecture (FIXED)
# This script builds a Docker image specifically for AMD64 (Intel/AMD) systems with FIXED database configuration

echo "🔨 Building Automan Client Docker Image for AMD64 (FIXED)..."

# Build the Docker image with AMD64 platform and FIXED database configuration
docker build -f Dockerfile.client.amd64.fixed -t automan-client:amd64-fixed .

if [ $? -eq 0 ]; then
    echo "✅ Docker image built successfully!"
    echo "📦 Image: automan-client:amd64-fixed"
    echo "🏗️  Architecture: AMD64 (Intel/AMD compatible)"
    echo "🔧 Database: FIXED MySQL configuration"
    echo ""
    echo "🚀 To run the application:"
    echo "   docker run -p 8080:8080 automan-client:amd64-fixed"
    echo ""
    echo "📤 To save the image for client delivery:"
    echo "   docker save automan-client:amd64-fixed | gzip > automan-client-amd64-fixed.tar.gz"
else
    echo "❌ Docker build failed!"
    exit 1
fi
