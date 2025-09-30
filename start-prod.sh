#!/bin/bash

echo "🚀 Starting Automan in PRODUCTION mode..."
echo "📦 This will build optimized containers (takes longer but better performance)"

# Stop any existing containers
docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
docker-compose down 2>/dev/null || true

# Start production environment
docker-compose up -d

echo "✅ Production environment started!"
echo "🌐 Frontend: http://localhost:8080"
echo "🔧 Backend: http://localhost:8083"
echo "🗄️  Database: localhost:3307"
echo "📊 phpMyAdmin: http://localhost:8082"
echo ""
echo "🛑 To stop: docker-compose down"
