#!/bin/bash

# Build Client Docker Image for MACBOOK
# This script builds a Docker image optimized for MacBooks

echo "🍎 Building Automan Client Docker Image for MACBOOK..."

# Build the Docker image for MacBook
docker build -f Dockerfile.client.mac -t automan-client:mac .

if [ $? -eq 0 ]; then
    echo "✅ Docker image built successfully for MACBOOK!"
    echo "📦 Image: automan-client:mac"
    echo "🏗️  Architecture: Multi-platform (Intel + Apple Silicon)"
    echo "🔧 Database: FIXED MySQL configuration"
    echo "📝 Code: LATEST updates included"
    echo ""
    echo "🚀 To run the application:"
    echo "   docker run -p 8080:8080 automan-client:mac"
    echo ""
    echo "📤 To save the image for client delivery:"
    echo "   docker save automan-client:mac | gzip > automan-client-mac.tar.gz"
    echo ""
    echo "🧪 To test the image:"
    echo "   docker run -p 8080:8080 automan-client:mac"
    echo "   # Then open http://localhost:8080 in your browser"
else
    echo "❌ Docker build failed!"
    exit 1
fi
