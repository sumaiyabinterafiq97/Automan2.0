#!/bin/bash

echo "🚀 Starting Automan in SIMPLE DEVELOPMENT mode..."
echo "📝 Frontend runs locally with hot reloading, backend in Docker"

# Stop any existing containers
docker-compose down 2>/dev/null || true

# Start only backend services
echo "🐳 Starting backend services (MySQL, Backend, phpMyAdmin)..."
docker-compose up -d mysql backend phpmyadmin

# Wait for backend to be ready
echo "⏳ Waiting for backend to be ready..."
sleep 15

# Check if backend is ready
echo "🔍 Checking backend status..."
if curl -f http://localhost:8083/actuator/health >/dev/null 2>&1; then
    echo "✅ Backend is ready!"
else
    echo "⚠️  Backend is still starting, but continuing..."
fi

# Start frontend development server
echo "🔥 Starting frontend development server..."
echo "💡 Your code changes will be reflected immediately!"

# Kill any existing process on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null || true

# Start the development server
./gradlew jsBrowserDevelopmentWebpack --continuous &
FRONTEND_PID=$!

# Wait a moment for the server to start
sleep 10

echo ""
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
