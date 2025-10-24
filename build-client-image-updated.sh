#!/bin/bash

# Build Client Docker Image with UPDATED Code
# This script builds a Docker image with all the latest code changes

echo "🔨 Building Automan Client Docker Image with UPDATED Code..."

# Build the Docker image with updated code
docker build -f Dockerfile.client.updated -t automan-client:updated .

if [ $? -eq 0 ]; then
    echo "✅ Docker image built successfully with UPDATED code!"
    echo "📦 Image: automan-client:updated"
    echo "🏗️  Architecture: AMD64 (Intel/AMD compatible)"
    echo "🔧 Database: FIXED MySQL configuration"
    echo "📝 Code: LATEST updates included"
    echo ""
    echo "🚀 To run the application:"
    echo "   docker run -p 8080:8080 automan-client:updated"
    echo ""
    echo "📤 To save the image for client delivery:"
    echo "   docker save automan-client:updated | gzip > automan-client-updated.tar.gz"
    echo ""
    echo "🧪 To test the image:"
    echo "   docker run -p 8080:8080 automan-client:updated"
    echo "   # Then open http://localhost:8080 in your browser"
else
    echo "❌ Docker build failed!"
    exit 1
fi
