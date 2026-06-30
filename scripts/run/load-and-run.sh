#!/bin/bash

echo "Automan Car Purchase System - Multi-Platform (FIXED)"
echo "======================================================"

# Check if Docker is running
if ! docker version >/dev/null 2>&1; then
    echo "[ERROR] Docker is not running. Please start Docker Desktop first."
    exit 1
fi
echo "[OK] Docker is running"

# Detect platform
ARCH=$(uname -m)
if [[ "$ARCH" == "arm64" || "$ARCH" == "aarch64" ]]; then
    PLATFORM="arm64"
    echo "[INFO] Detected Apple Silicon (ARM64) - Loading ARM64 images"
elif [[ "$ARCH" == "x86_64" ]]; then
    PLATFORM="amd64"
    echo "[INFO] Detected Intel/AMD64 - Loading AMD64 images"
else
    echo "[ERROR] Unsupported architecture: $ARCH"
    exit 1
fi

# Load the appropriate images
echo "[LOADING] Loading multi-platform images..."
docker load -i automan-multiplatform-client-final-fixed-corrected.tar

if [ $? -ne 0 ]; then
    echo "[ERROR] Failed to load Docker images. Please ensure 'automan-multiplatform-client-final-fixed-corrected.tar' is in the same directory."
    exit 1
fi
echo "[OK] Multi-platform images loaded successfully"

# Tag the correct images for the current platform
if [[ "$PLATFORM" == "arm64" ]]; then
    docker tag automan20-backend:multiplatform automan20-backend:latest
    docker tag automan20-frontend:multiplatform automan20-frontend:latest
else
    docker tag automan20-backend:amd64 automan20-backend:latest
    docker tag automan20-frontend:amd64 automan20-frontend:latest
fi

# Stop any existing containers
echo "[STOP] Stopping any existing containers..."
docker-compose down 2>/dev/null || true

# Start the system
echo "[START] Starting Automan system..."
docker-compose up -d

if [ $? -eq 0 ]; then
    echo "[OK] Automan system started successfully!"
    echo ""
    echo "[WAIT] Waiting for services to initialize (this may take 1-2 minutes)..."
    sleep 60
    
    echo "[WEB] Access Points:"
    echo "   - Main Application: http://localhost:8080"
    echo "   - Backend API: http://localhost:8083/api"
    echo "   - Database Admin: http://localhost:8084"
    echo "   - MySQL Direct: localhost:3306"
    echo ""
    echo "[LOGIN] Pre-configured Login:"
    echo "   - Email: admin@automan.com"
    echo "   - Password: Automan!Ship26Tokyo"
    echo ""
    echo "[DATA] Pre-populated Data:"
    echo "   - 1 Admin user"
    echo "   - 1 Client (Tokyo Auto Import)"
    echo "   - 4 Sample purchases"
    echo ""
    echo "[TOOLS] Management Commands:"
    echo "   - Stop: docker-compose down"
    echo "   - Restart: docker-compose restart"
    echo "   - Logs: docker-compose logs -f"
    echo "   - Status: docker-compose ps"
    echo ""
    echo "[SUCCESS] System is ready! Open http://localhost:8080 in your browser."
else
    echo "[ERROR] Failed to start system"
    exit 1
fi

