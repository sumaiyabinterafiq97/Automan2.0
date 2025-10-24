@echo off
REM Automan Car Purchase Management System - Client Startup Script for Windows
REM This script starts the complete system for clients

echo 🚗 Starting Automan Car Purchase Management System...
echo ==================================================

REM Check if Docker is running
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not running. Please start Docker Desktop first.
    echo    - Open Docker Desktop from Start Menu
    echo    - Wait for Docker to fully start
    pause
    exit /b 1
)

echo ✅ Docker is running

REM Stop any existing containers
echo 🔄 Stopping any existing containers...
docker-compose -f docker-compose.client.yml down >nul 2>&1

REM Start the system
echo 🚀 Starting the system...
docker-compose -f docker-compose.client.yml up -d

REM Wait for services to start
echo ⏳ Waiting for services to start (this may take 2-3 minutes)...
timeout /t 30 /nobreak >nul

REM Check if services are running
echo 🔍 Checking service status...
docker-compose -f docker-compose.client.yml ps

echo.
echo 🎉 System is starting up!
echo ==================================================
echo 📱 Access Points:
echo    • Main Application: http://localhost:9090
echo    • Database Admin:   http://localhost:8082
echo.
echo 🔑 Login Credentials:
echo    • Email:    admin@gmail.com
echo    • Password: admin123
echo.
echo ⏳ Please wait 2-3 minutes for all services to fully start
echo    Then open http://localhost:9090 in your browser
echo.
echo 🛠️  To stop the system: docker-compose -f docker-compose.client.yml down
echo 🔄 To restart: start-client.bat
echo ==================================================
pause
