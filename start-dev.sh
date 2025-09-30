#!/bin/bash

echo "🚀 Starting Automan in DEVELOPMENT mode with hot reloading..."
echo "📝 Changes to your code will be reflected immediately without rebuilding!"

# Stop any existing containers
docker-compose down 2>/dev/null || true

# Start development environment
docker-compose -f docker-compose.dev.yml up -d

echo "✅ Development environment started!"
echo "🌐 Frontend: http://localhost:8080 (with hot reloading)"
echo "🔧 Backend: http://localhost:8083"
echo "🗄️  Database: localhost:3307"
echo "📊 phpMyAdmin: http://localhost:8082"
echo ""
echo "💡 Your code changes will be reflected immediately!"
echo "🛑 To stop: docker-compose -f docker-compose.dev.yml down"
