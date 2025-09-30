#!/bin/bash

echo "🚀 Starting Automan in LOCAL DEVELOPMENT mode..."
echo "📝 This runs the frontend locally with hot reloading (no Docker rebuilds needed!)"

# Stop any existing containers
docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
docker-compose down 2>/dev/null || true

# Start only backend services in Docker
echo "🐳 Starting backend services (MySQL, Backend, phpMyAdmin)..."
docker-compose up -d mysql backend phpmyadmin

# Wait for backend to be ready
echo "⏳ Waiting for backend to be ready..."
sleep 10

# Start frontend locally with hot reloading
echo "🔥 Starting frontend locally with hot reloading..."
echo "💡 Your code changes will be reflected immediately!"

# Run the frontend development server locally
./gradlew jsBrowserDevelopmentWebpack --continuous &
FRONTEND_PID=$!

echo "✅ Development environment started!"
echo "🌐 Frontend: http://localhost:8080 (with hot reloading)"
echo "🔧 Backend: http://localhost:8083"
echo "🗄️  Database: localhost:3307"
echo "📊 phpMyAdmin: http://localhost:8082"
echo ""
echo "💡 Your code changes will be reflected immediately!"
echo "🛑 To stop: Press Ctrl+C or run: kill $FRONTEND_PID && docker-compose down"

# Wait for user to stop
wait $FRONTEND_PID
