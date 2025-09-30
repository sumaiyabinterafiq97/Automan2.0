#!/bin/bash

echo "🚀 Starting Automan with HOT RELOADING..."
echo "📝 Changes to your code will be reflected immediately without rebuilding!"
echo "⚡ This uses volume mounting for instant updates!"

# Stop any existing containers
docker-compose down 2>/dev/null || true
docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
docker-compose -f docker-compose.hot-reload.yml down 2>/dev/null || true

# Start hot reload environment
docker-compose -f docker-compose.hot-reload.yml up -d

echo "✅ Hot reload environment started!"
echo "🌐 Frontend: http://localhost:8080 (with hot reloading)"
echo "🔧 Backend: http://localhost:8083"
echo "🗄️  Database: localhost:3307"
echo "📊 phpMyAdmin: http://localhost:8082"
echo ""
echo "💡 Your code changes will be reflected immediately!"
echo "📁 Source code is mounted as volumes for instant updates"
echo "🛑 To stop: docker-compose -f docker-compose.hot-reload.yml down"
echo ""
echo "🔍 To watch logs: docker-compose -f docker-compose.hot-reload.yml logs -f frontend"
