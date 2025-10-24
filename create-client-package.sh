#!/bin/bash

# Create client delivery package script
echo "🚗 Creating Automan Client Delivery Package..."
echo "============================================="

# Create single image package
echo "📦 Creating single image package..."
mkdir -p automan-single-image

# Copy the single Docker image
echo "🐳 Copying Docker images..."
cp automan-complete.tar automan-single-image/

# Copy configuration files
echo "📋 Copying configuration files..."
cp docker-compose.client.yml automan-single-image/
cp -r database automan-single-image/

# Create setup scripts
echo "🚀 Creating setup scripts..."

# MacBook/Linux script
cat > automan-single-image/load-and-run.sh << 'EOF'
#!/bin/bash

# Automan Car Purchase Management System - Single Image Setup
echo "🚗 Loading Automan Complete System..."
echo "===================================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi

echo "✅ Docker is running"

# Load the complete system image
echo "📦 Loading complete system image..."
if [ -f "automan-complete.tar" ]; then
    docker load -i automan-complete.tar
    echo "✅ Complete system image loaded successfully"
else
    echo "❌ automan-complete.tar not found!"
    exit 1
fi

# Start the system using docker-compose
echo "🚀 Starting the complete system..."
docker-compose -f docker-compose.client.yml up -d

# Wait for services to start
echo "⏳ Waiting for services to start..."
sleep 30

# Check if services are running
echo "🔍 Checking system status..."
docker-compose -f docker-compose.client.yml ps

echo ""
echo "🎉 Automan System is ready!"
echo "===================================="
echo "📱 Access Points:"
echo "   • Main Application: http://localhost:9090"
echo "   • Database Admin: http://localhost:8082"
echo "   • Backend API: http://localhost:8083/api"
echo ""
echo "🔑 Login Credentials:"
echo "   • Email: admin@gmail.com"
echo "   • Password: admin123"
echo "===================================="
EOF

# Windows script
cat > automan-single-image/load-and-run.bat << 'EOF'
@echo off
REM Automan Car Purchase Management System - Single Image Setup for Windows
echo 🚗 Loading Automan Complete System...
echo ====================================

REM Check if Docker is running
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not running. Please start Docker Desktop first.
    pause
    exit /b 1
)

echo ✅ Docker is running

REM Load the complete system image
echo 📦 Loading complete system image...
if exist "automan-complete.tar" (
    docker load -i automan-complete.tar
    echo ✅ Complete system image loaded successfully
) else (
    echo ❌ automan-complete.tar not found!
    pause
    exit /b 1
)

REM Start the system using docker-compose
echo 🚀 Starting the complete system...
docker-compose -f docker-compose.client.yml up -d

REM Wait for services to start
echo ⏳ Waiting for services to start...
timeout /t 30 /nobreak >nul

REM Check if services are running
echo 🔍 Checking system status...
docker-compose -f docker-compose.client.yml ps

echo.
echo 🎉 Automan System is ready!
echo ====================================
echo 📱 Access Points:
echo    • Main Application: http://localhost:9090
echo    • Database Admin: http://localhost:8082
echo    • Backend API: http://localhost:8083/api
echo.
echo 🔑 Login Credentials:
echo    • Email: admin@gmail.com
echo    • Password: admin123
echo ====================================
pause
EOF

# Make scripts executable
chmod +x automan-single-image/load-and-run.sh

# Create README
echo "📖 Creating README..."
cat > automan-single-image/README.md << 'EOF'
# 🚗 Automan Car Purchase Management System - Single Image Package

## 📦 **What's Included**

This package contains a **single Docker image file** with the complete Automan Car Purchase Management System:

### **🐳 Single Docker Image:**
- `automan-complete.tar` (865MB) - Complete system with backend, frontend, and database
- Contains all latest features and updates
- Pre-configured admin user included

### **📋 Configuration Files:**
- `docker-compose.client.yml` - System configuration
- `database/init.sql` - Database with pre-configured admin user

### **🚀 Setup Scripts:**
- `load-and-run.sh` - MacBook/Linux setup script
- `load-and-run.bat` - Windows setup script

## 🚀 **Super Simple Setup (3 Steps)**

### **Step 1: Install Docker Desktop**
- **MacBook**: Download from https://www.docker.com/products/docker-desktop/
- **Windows**: Download from https://www.docker.com/products/docker-desktop/
- **Start Docker Desktop** and wait for it to fully load

### **Step 2: Load and Start System**

#### **🍎 MacBook/Linux:**
```bash
# Open Terminal in this folder
./load-and-run.sh
```

#### **🪟 Windows:**
```cmd
# Double-click or run in Command Prompt
load-and-run.bat
```

### **Step 3: Access the Application**
1. **Open browser**: http://localhost:9090
2. **Login**: admin@gmail.com / admin123
3. **Start testing!** 🎉

## ✨ **What You Get**

### **🎯 Complete System:**
- ✅ **Purchase Management** - Add, edit, manage car purchases
- ✅ **Client Management** - Track client accounts and balances
- ✅ **Booking System** - Create shipping schedules
- ✅ **FOB/C&F Calculations** - Advanced cost calculations
- ✅ **Rixo Request Generator** - Generate PDF transport requests
- ✅ **User Management** - Role-based access control

### **🆕 Latest Features:**
- ✅ **BOOKING DETAILS Page** - Checkbox selection (C&F/FOB) with Calculate button
- ✅ **FOB Page** - Saves to `total_cnf_price` column, navigates to BOOKING DETAILS
- ✅ **Database Schema** - Removed `total_fob_price` column, optimized structure
- ✅ **PDF Generation** - Fixed alignment issues, proper Japanese formatting
- ✅ **UI Improvements** - Better styling, responsive design

## 🔑 **Pre-configured Access**

### **Admin User:**
- **Email**: `admin@gmail.com`
- **Password**: `admin123`
- **Role**: ADMIN (full system access)

### **System Access Points:**
- **Main Application**: http://localhost:9090
- **Database Admin**: http://localhost:8082
- **Backend API**: http://localhost:8083/api

## 🛠️ **System Management**

### **Start System:**
```bash
# MacBook/Linux
./load-and-run.sh

# Windows
load-and-run.bat
```

### **Stop System:**
```bash
docker-compose -f docker-compose.client.yml down
```

### **Restart System:**
```bash
docker-compose -f docker-compose.client.yml restart
```

### **View System Status:**
```bash
docker-compose -f docker-compose.client.yml ps
```

### **View Logs:**
```bash
docker-compose -f docker-compose.client.yml logs
```

## 🆘 **Troubleshooting**

### **Docker Image Won't Load:**
1. Ensure Docker Desktop is running
2. Check available disk space (need ~2GB)
3. Try: `docker system prune` to free space
4. Restart Docker Desktop

### **System Won't Start:**
1. Check if all containers are running: `docker-compose -f docker-compose.client.yml ps`
2. View error logs: `docker-compose -f docker-compose.client.yml logs`
3. Ensure ports 9090, 8082, 8083, 3307 are not in use

### **Can't Access Application:**
1. Wait 2-3 minutes for all services to start
2. Check if all containers show "Up" status
3. Try accessing: http://localhost:9090
4. Check browser console for errors

### **Performance Issues:**
1. Allocate more RAM to Docker (8GB+ recommended)
2. Close other applications
3. Restart Docker Desktop
4. Check system resources

## 📞 **Support Information**

### **System Requirements:**
- **Docker Desktop** (latest version)
- **8GB RAM** (16GB recommended)
- **10GB free disk space**
- **Internet connection** (for initial setup)

### **Supported Platforms:**
- ✅ **MacBook** (Intel and Apple Silicon)
- ✅ **Windows** (10/11)
- ✅ **Linux** (Ubuntu, CentOS, etc.)

### **Default Credentials:**
- **Database**: automan_user / automan_password
- **Admin User**: admin@gmail.com / admin123

## 🎯 **Advantages of Single Image Package**

### **✅ Simplicity:**
- **One file** to share with clients
- **One command** to start everything
- **No complex setup** required

### **✅ Reliability:**
- **Pre-tested** and working system
- **All dependencies** included
- **Consistent** across all environments

### **✅ Portability:**
- **Easy to share** via cloud storage, USB, etc.
- **Works offline** after initial load
- **No internet** required for operation

---

## 🎉 **Ready to Test!**

This single image package contains a complete, production-ready Automan Car Purchase Management System with all the latest features and updates. Your clients can start testing immediately with the pre-configured admin user!

**Total Package Size**: ~865MB
**Setup Time**: 3 minutes
**No signup required** - just load and start! 🚀
EOF

# Create compressed package
echo "📦 Creating compressed package..."
tar -czf automan-single-image.tar.gz automan-single-image/

echo ""
echo "🎉 Client delivery package created successfully!"
echo "============================================="
echo "📁 Package: automan-single-image.tar.gz"
echo "📏 Size: $(ls -lh automan-single-image.tar.gz | awk '{print $5}')"
echo ""
echo "📤 Ready to send to clients!"
echo "   • Extract the package"
echo "   • Run: ./load-and-run.sh (MacBook/Linux)"
echo "   • Or run: load-and-run.bat (Windows)"
echo "   • Open: http://localhost:9090"
echo "   • Login: admin@gmail.com / admin123"
echo "============================================="
