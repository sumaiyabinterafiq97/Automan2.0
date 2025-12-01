#!/bin/bash

echo "🚗 Automan Car Purchase System - Flyway Migration Version"
echo "======================================================"

# Check if Docker is running
if ! docker version >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi
echo "✅ Docker is running"

# Load the Flyway images
echo "📥 Loading Flyway migration images..."
docker load -i automan-complete-flyway.tar

if [ $? -ne 0 ]; then
    echo "❌ Failed to load images. Make sure 'automan-complete-flyway.tar' is in the same directory."
    exit 1
fi
echo "✅ Flyway migration images loaded successfully"

# Stop any existing containers
echo "🛑 Stopping any existing containers..."
docker-compose down 2>/dev/null || true

# Start the system
echo "🚀 Starting Automan system with Flyway migrations..."
docker-compose up -d

if [ $? -eq 0 ]; then
    echo "✅ Automan system with Flyway migrations started successfully!"
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
    echo "📊 Pre-populated Data (via Flyway migrations):"
    echo "   • 1 Admin user"
    echo "   • 1 Client (Tokyo Auto Import)"
    echo "   • 4 Sample purchases"
    echo "   • Sample events and vessels"
    echo "   • Automatic database schema setup"
    echo ""
    echo "🛠️ Management Commands:"
    echo "   • Stop: docker-compose down"
    echo "   • Restart: docker-compose restart"
    echo "   • Logs: docker-compose logs"
    echo "   • Remove: docker-compose down -v"
    echo ""
    echo "🎯 Flyway Migration Benefits:"
    echo "   • Professional database version control"
    echo "   • Automatic schema setup and updates"
    echo "   • Safe database migrations with rollback"
    echo "   • Consistent schema across all environments"
else
    echo "❌ Failed to start system"
    exit 1
fi
