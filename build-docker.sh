#!/bin/bash

echo "🚀 Building and starting Automan Application with Docker..."

# Stop and remove existing containers
echo "🛑 Stopping existing containers..."
docker-compose down

# Remove existing images to ensure fresh build
echo "🧹 Removing existing images..."
docker-compose down --rmi all

# Build and start all services
echo "🔨 Building and starting services..."
docker-compose up --build -d

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 30

# Check service status
echo "📊 Checking service status..."
docker-compose ps

# Show logs
echo "📋 Recent logs:"
docker-compose logs --tail=20

echo ""
echo "✅ Automan Application is now running!"
echo ""
echo "🌐 Frontend: http://localhost:8080"
echo "🔧 Backend API: http://localhost:8083"
echo "🗄️  phpMyAdmin: http://localhost:8082"
echo "   - Username: automan_user"
echo "   - Password: automan_password"
echo ""
echo "📱 To stop the application: docker-compose down"
echo "📱 To view logs: docker-compose logs -f"
echo "📱 To restart: docker-compose restart"
