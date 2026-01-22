#!/bin/bash

# Automan Car Purchase Management System - Client Startup Script
# This script starts the complete system for clients

echo "🚗 Starting Automan Car Purchase Management System..."
echo "=================================================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    echo "   - MacBook: Open Docker Desktop from Applications"
    echo "   - Windows: Start Docker Desktop from Start Menu"
    exit 1
fi

echo "✅ Docker is running"

# Stop any existing containers
echo "🔄 Stopping any existing containers..."
docker-compose -f docker-compose.client.yml down > /dev/null 2>&1

# Start the system
echo "🚀 Starting the system..."
docker-compose -f docker-compose.client.yml up -d

# Wait for services to start
echo "⏳ Waiting for services to start (this may take 2-3 minutes)..."
sleep 30

# Check if services are running
echo "🔍 Checking service status..."
docker-compose -f docker-compose.client.yml ps

echo ""
echo "🎉 System is starting up!"
echo "=================================================="
echo "📱 Access Points:"
echo "   • Main Application: http://localhost:9090"
echo "   • Database Admin:   http://localhost:8082"
echo ""
echo "🔑 Login Credentials:"
echo "   • Email:    admin@gmail.com"
echo "   • Password: admin123"
echo ""
echo "⏳ Please wait 2-3 minutes for all services to fully start"
echo "   Then open http://localhost:9090 in your browser"
echo ""
echo "🛠️  To stop the system: docker-compose -f docker-compose.client.yml down"
echo "🔄 To restart: ./start-client.sh"
echo "=================================================="