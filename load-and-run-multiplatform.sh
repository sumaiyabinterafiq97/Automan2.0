#!/bin/bash

echo "🚗 Automan Car Purchase System - Multi-Platform"
echo "=============================================="

# Check if Docker is running
if ! docker version >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi
echo "✅ Docker is running"

# Load the multi-platform images
echo "📥 Loading multi-platform images..."
docker load -i automan-complete-multiplatform.tar

if [ $? -ne 0 ]; then
    echo "❌ Failed to load images. Make sure 'automan-complete-multiplatform.tar' is in the same directory."
    exit 1
fi
echo "✅ Multi-platform images loaded successfully"

# Stop any existing containers
echo "🛑 Stopping any existing containers..."
docker-compose -f docker-compose.multiplatform.yml down 2>/dev/null || true

# Start the system
echo "🚀 Starting Automan system..."
docker-compose -f docker-compose.multiplatform.yml up -d

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
    echo "   • Stop: docker-compose -f docker-compose.multiplatform.yml down"
    echo "   • Restart: docker-compose -f docker-compose.multiplatform.yml restart"
    echo "   • Logs: docker-compose -f docker-compose.multiplatform.yml logs"
    echo "   • Remove: docker-compose -f docker-compose.multiplatform.yml down -v"
else
    echo "❌ Failed to start system"
    exit 1
fi
