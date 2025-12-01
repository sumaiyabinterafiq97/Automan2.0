#!/bin/bash

echo "🚀 Building Multi-Platform Automan Docker Images (UPDATED)"
echo "=========================================================="

# Set image names
BACKEND_IMAGE="automan20-backend"
FRONTEND_IMAGE="automan20-frontend"

# Create buildx builder if it doesn't exist
echo "🔧 Setting up buildx builder..."
docker buildx create --name multiplatform-builder --use 2>/dev/null || docker buildx use multiplatform-builder

# Build backend for AMD64
echo ""
echo "📦 Building Backend Image for AMD64..."
cd backend
docker buildx build \
    --platform linux/amd64 \
    --file Dockerfile \
    --tag ${BACKEND_IMAGE}:amd64 \
    --output type=docker \
    --load \
    .

if [ $? -ne 0 ]; then
    echo "❌ Backend AMD64 build failed!"
    exit 1
fi
echo "✅ Backend AMD64 image built successfully"

# Build backend for ARM64
echo ""
echo "📦 Building Backend Image for ARM64..."
docker buildx build \
    --platform linux/arm64 \
    --file Dockerfile \
    --tag ${BACKEND_IMAGE}:arm64 \
    --output type=docker \
    --load \
    .

if [ $? -ne 0 ]; then
    echo "❌ Backend ARM64 build failed!"
    exit 1
fi
echo "✅ Backend ARM64 image built successfully"

# Tag multiplatform
docker tag ${BACKEND_IMAGE}:amd64 ${BACKEND_IMAGE}:multiplatform

# Build frontend for AMD64
echo ""
echo "📦 Building Frontend Image for AMD64..."
cd ..
docker buildx build \
    --platform linux/amd64 \
    --file backend/Dockerfile.frontend \
    --tag ${FRONTEND_IMAGE}:amd64 \
    --output type=docker \
    --load \
    .

if [ $? -ne 0 ]; then
    echo "❌ Frontend AMD64 build failed!"
    exit 1
fi
echo "✅ Frontend AMD64 image built successfully"

# Build frontend for ARM64
echo ""
echo "📦 Building Frontend Image for ARM64..."
docker buildx build \
    --platform linux/arm64 \
    --file backend/Dockerfile.frontend \
    --tag ${FRONTEND_IMAGE}:arm64 \
    --output type=docker \
    --load \
    .

if [ $? -ne 0 ]; then
    echo "❌ Frontend ARM64 build failed!"
    exit 1
fi
echo "✅ Frontend ARM64 image built successfully"

# Tag multiplatform
docker tag ${FRONTEND_IMAGE}:amd64 ${FRONTEND_IMAGE}:multiplatform

# Export images to tar file
echo ""
echo "💾 Exporting images to tar file..."
docker save ${BACKEND_IMAGE}:amd64 ${BACKEND_IMAGE}:arm64 ${BACKEND_IMAGE}:multiplatform \
         ${FRONTEND_IMAGE}:amd64 ${FRONTEND_IMAGE}:arm64 ${FRONTEND_IMAGE}:multiplatform \
         -o automan-multiplatform-complete-updated.tar

if [ $? -eq 0 ]; then
    echo "✅ Images exported successfully!"
    echo "📁 File: automan-multiplatform-complete-updated.tar"
    ls -lh automan-multiplatform-complete-updated.tar
else
    echo "❌ Export failed!"
    exit 1
fi

echo ""
echo "🎉 Multi-platform build completed successfully!"
echo ""
echo "📋 Built Images:"
echo "   • ${BACKEND_IMAGE}:amd64"
echo "   • ${BACKEND_IMAGE}:arm64"
echo "   • ${BACKEND_IMAGE}:multiplatform"
echo "   • ${FRONTEND_IMAGE}:amd64"
echo "   • ${FRONTEND_IMAGE}:arm64"
echo "   • ${FRONTEND_IMAGE}:multiplatform"
echo ""
echo "📦 Package: automan-multiplatform-complete-updated.tar"

