@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

echo 🚗 Automan Car Purchase System - Windows Compatible Version
echo ========================================================

:: Check if Docker is running
docker version > NUL 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker is not running. Please start Docker Desktop first.
    GOTO :EOF
)
echo ✅ Docker is running

:: Load the Windows-compatible images
echo 📥 Loading Windows-compatible images...
docker load -i automan-windows-compatible.tar
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to load images. Make sure 'automan-windows-compatible.tar' is in the same directory.
    GOTO :EOF
)
echo ✅ Windows-compatible images loaded successfully

:: Stop any existing containers
echo 🛑 Stopping any existing containers...
docker-compose down 2>NUL

:: Start the system
echo 🚀 Starting Automan system with Windows-compatible images...
docker-compose up -d
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to start system
    GOTO :EOF
)

echo ✅ Automan system with Windows-compatible images started successfully!
echo.
echo 🌐 Access Points:
echo    • Frontend: http://localhost:8080
echo    • Backend API: http://localhost:8083/api
echo    • MySQL: localhost:3306
echo.
echo 🔑 Pre-configured Login:
echo    • Email: admin@automan.com
echo    • Password: admin123
echo.
echo 📊 Pre-populated Data (via Flyway migrations):
echo    • 1 Admin user
echo    • 1 Client (Tokyo Auto Import)
echo    • 4 Sample purchases
echo    • Sample events and vessels
echo    • Automatic database schema setup
echo.
echo 🛠️ Management Commands:
echo    • Stop: docker-compose down
echo    • Restart: docker-compose restart
echo    • Logs: docker-compose logs
echo    • Remove: docker-compose down -v
echo.
echo 🎯 Windows Compatibility Benefits:
echo    • Built specifically for Windows (linux/amd64)
echo    • No platform mismatch issues
echo    • Optimized for Intel/AMD processors
echo    • Professional database management with Flyway

PAUSE
