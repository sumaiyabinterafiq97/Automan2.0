@echo off
echo 🚗 Automan Car Purchase System - Multi-Platform
echo ==============================================

REM Check if Docker is running
docker version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not running. Please start Docker Desktop first.
    pause
    exit /b 1
)
echo ✅ Docker is running

REM Pull the multi-platform image
echo 📥 Pulling multi-platform image...
docker pull automan-multiplatform:latest

if %errorlevel% neq 0 (
    echo ❌ Failed to pull image. Make sure the image exists.
    pause
    exit /b 1
)

REM Stop any existing containers
echo 🛑 Stopping any existing containers...
docker stop automan-multiplatform 2>nul
docker rm automan-multiplatform 2>nul

REM Run the multi-platform container
echo 🚀 Starting Automan system...
docker run -d ^
    --name automan-multiplatform ^
    -p 8080:8080 ^
    -p 8083:8083 ^
    -p 3306:3306 ^
    automan-multiplatform:latest

if %errorlevel% equ 0 (
    echo ✅ Automan system started successfully!
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
    echo 📊 Pre-populated Data:
    echo    • 1 Admin user
    echo    • 1 Client (Tokyo Auto Import)
    echo    • 4 Sample purchases
    echo    • Sample events and vessels
    echo.
    echo 🛠️ Management Commands:
    echo    • Stop: docker stop automan-multiplatform
    echo    • Restart: docker restart automan-multiplatform
    echo    • Logs: docker logs automan-multiplatform
    echo    • Remove: docker rm -f automan-multiplatform
) else (
    echo ❌ Failed to start system
    pause
    exit /b 1
)

pause
