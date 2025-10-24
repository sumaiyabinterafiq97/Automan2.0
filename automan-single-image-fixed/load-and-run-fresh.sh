#!/bin/bash

echo "=================================================="
echo "Loading Automan Complete System (FRESH START)..."
echo "=================================================="

# Check if Docker is running
if ! docker version >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi
echo "✅ Docker is running"

# Load the complete system image
echo "🔄 Loading complete system image..."
docker load -i automan-complete-fresh.tar
if [ $? -ne 0 ]; then
    echo "❌ Failed to load Docker image"
    exit 1
fi
echo "✅ Complete system image loaded successfully"

# Start the complete system
echo "🚀 Starting the complete system..."
docker-compose -f docker-compose.client.yml up -d
if [ $? -ne 0 ]; then
    echo "❌ Failed to start system"
    exit 1
fi

echo "⏳ Waiting for services to start..."
sleep 30

# Check system status
echo "🔍 Checking system status..."
docker-compose -f docker-compose.client.yml ps

echo ""
echo "✅ Automan System is ready!"
echo "=================================================="
echo "🌐 Access Points:"
echo "   • Main Application: http://localhost:8003"
echo "   • Database Admin: http://localhost:8004"
echo "   • Backend API: http://localhost:8002/api"
echo ""
echo "🎯 FIRST-TIME SETUP:"
echo "   1. Open browser: http://localhost:8003"
echo "   2. You'll see the Sign Up page"
echo "   3. Create your admin account with your preferred credentials"
echo "   4. Start using the system!"
echo ""
echo "📋 Sample Data Included:"
echo "   • Sample purchases, clients, and transactions"
echo "   • No pre-configured users - you create your own!"
echo "=================================================="
echo ""
echo "🛠️ Management Commands:"
echo "   • Stop system: docker-compose -f docker-compose.client.yml down"
echo "   • Restart system: docker-compose -f docker-compose.client.yml restart"
echo "   • View logs: docker-compose -f docker-compose.client.yml logs"
echo "=================================================="
