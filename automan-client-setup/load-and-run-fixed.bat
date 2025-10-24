@echo off
echo ==================================================
echo Loading Automan Complete System (FIXED VERSION)...
echo ==================================================

REM Check if Docker is running
docker version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not running. Please start Docker Desktop first.
    pause
    exit /b 1
)
echo ✅ Docker is running

REM Load the complete system image
echo 🔄 Loading complete system image...
docker load -i automan-complete-fixed.tar
if %errorlevel% neq 0 (
    echo ❌ Failed to load Docker image
    pause
    exit /b 1
)
echo ✅ Complete system image loaded successfully

REM Start the complete system
echo 🚀 Starting the complete system...
docker-compose -f docker-compose.client.yml up -d
if %errorlevel% neq 0 (
    echo ❌ Failed to start system
    pause
    exit /b 1
)

echo ⏳ Waiting for services to start...
timeout /t 30 /nobreak >nul

REM Check system status
echo 🔍 Checking system status...
docker-compose -f docker-compose.client.yml ps

echo.
echo ✅ Automan System is ready!
echo ==================================================
echo 🌐 Access Points:
echo    • Main Application: http://localhost:8003
echo    • Database Admin: http://localhost:8004
echo    • Backend API: http://localhost:8002/api
echo.
echo 🔑 Login Credentials:
echo    • Email: admin@gmail.com
echo    • Password: password
echo ==================================================
echo.
echo 🛠️ Management Commands:
echo    • Stop system: docker-compose -f docker-compose.client.yml down
echo    • Restart system: docker-compose -f docker-compose.client.yml restart
echo    • View logs: docker-compose -f docker-compose.client.yml logs
echo ==================================================
pause
