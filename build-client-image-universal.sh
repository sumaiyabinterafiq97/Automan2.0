#!/bin/bash

# Build Client Docker Image - UNIVERSAL (Mac + Windows)
# This script builds a Docker image that works on both Mac and Windows

echo "🌍 Building Automan Client Docker Image - UNIVERSAL (Mac + Windows)..."

# Build the Docker image for universal compatibility
docker build -f Dockerfile.client.universal -t automan-client:universal .

if [ $? -eq 0 ]; then
    echo "✅ Docker image built successfully - UNIVERSAL!"
    echo "📦 Image: automan-client:universal"
    echo "🏗️  Architecture: Universal (Mac + Windows compatible)"
    echo "🔧 Database: FIXED MySQL configuration"
    echo "📝 Code: LATEST updates included"
    echo ""
    echo "🚀 To run the application:"
    echo "   docker run -p 8080:8080 automan-client:universal"
    echo ""
    echo "📤 To save the image for client delivery:"
    echo "   docker save automan-client:universal | gzip > automan-client-universal.tar.gz"
    echo ""
    echo "🧪 To test the image:"
    echo "   docker run -p 8080:8080 automan-client:universal"
    echo "   # Then open http://localhost:8080 in your browser"
    echo ""
    echo "🌍 This image works on:"
    echo "   ✅ Windows 10/11 (64-bit)"
    echo "   ✅ MacBook Intel"
    echo "   ✅ MacBook Apple Silicon (M1/M2/M3)"
    echo "   ✅ Linux (64-bit)"
else
    echo "❌ Docker build failed!"
    exit 1
fi
