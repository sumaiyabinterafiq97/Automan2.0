@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

echo Automan Car Purchase System - Multi-Platform (FIXED)
echo ======================================================

:: Check if Docker is running
docker info > NUL 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker is not running. Please start Docker Desktop first.
    GOTO :EOF
)
echo [OK] Docker is running

:: Windows is always AMD64
set PLATFORM=amd64
echo [INFO] Windows detected - Loading AMD64 images

echo [LOADING] Loading multi-platform images...
docker load -i automan-multiplatform-client-final-fixed-corrected.tar
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to load Docker images. Please ensure 'automan-multiplatform-client-final-fixed-corrected.tar' is in the same directory.
    GOTO :EOF
)
echo [OK] Multi-platform images loaded successfully

:: Tag the correct images for Windows (AMD64)
docker tag automan20-backend:amd64 automan20-backend:latest
docker tag automan20-frontend:amd64 automan20-frontend:latest

:: Stop any existing containers
echo [STOP] Stopping any existing containers...
docker-compose down 2>NUL

:: Start the system
echo [START] Starting Automan system...
docker-compose up -d
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to start system
    GOTO :EOF
)

echo [OK] Automan system started successfully!
echo.
echo [WAIT] Waiting for services to initialize (this may take 1-2 minutes)...
timeout /t 60 /nobreak > NUL

echo [WEB] Access Points:
echo    - Main Application: http://localhost:8080
echo    - Backend API: http://localhost:8083/api
echo    - Database Admin: http://localhost:8084
echo    - MySQL Direct: localhost:3306
echo.
echo [LOGIN] Pre-configured Login:
echo    - Email: admin@automan.com
echo    - Password: password
echo.
echo [DATA] Pre-populated Data:
echo    - 1 Admin user
echo    - 1 Client (Tokyo Auto Import)
echo    - 4 Sample purchases
echo.
echo [TOOLS] Management Commands:
echo    - Stop: docker-compose down
echo    - Restart: docker-compose restart
echo    - Logs: docker-compose logs -f
echo    - Status: docker-compose ps
echo.
echo [SUCCESS] System is ready! Open http://localhost:8080 in your browser.

PAUSE

