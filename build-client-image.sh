#!/bin/bash

echo "🐳 Building Single Docker Image for Client Delivery..."

# Build the client image
echo "🔨 Building Automan Client Image..."
docker build -f Dockerfile.client -t automan-client:latest .

if [ $? -eq 0 ]; then
    echo "✅ Client image built successfully!"
    echo ""
    echo "📦 Image Details:"
    echo "   Name: automan-client:latest"
    echo "   Size: $(docker images automan-client:latest --format "table {{.Size}}" | tail -n 1)"
    echo ""
    echo "🚀 To run the client image:"
    echo "   docker run -p 8080:8080 automan-client:latest"
    echo ""
    echo "📋 To save as tar file for client:"
    echo "   docker save automan-client:latest -o automan-client.tar"
    echo ""
    echo "📤 To load on client machine:"
    echo "   docker load -i automan-client.tar"
else
    echo "❌ Build failed!"
    exit 1
fi
