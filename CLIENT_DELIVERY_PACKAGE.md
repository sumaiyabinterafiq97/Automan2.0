# 🚗 Automan Car Purchase Management System - Client Delivery Package

## 📦 **Package Contents**

This package contains everything your clients need to run the complete Automan Car Purchase Management System:

### **📁 Files Included:**
- `docker-compose.client.yml` - Main Docker configuration
- `CLIENT_SETUP_GUIDE.md` - Detailed setup instructions
- `start-client.sh` - MacBook/Linux startup script
- `start-client.bat` - Windows startup script
- `database/init.sql` - Database with pre-configured admin user

### **🐳 Docker Images (Pre-built):**
- `automan20-backend:latest` - Backend API with all latest features
- `automan20-frontend:latest` - Frontend with all latest UI changes
- `mysql:8.0` - Database server
- `phpmyadmin/phpmyadmin` - Database administration

## 🚀 **Quick Start for Clients**

### **🍎 MacBook Users:**
1. **Install Docker Desktop for Mac**
2. **Extract this package to a folder**
3. **Open Terminal in the folder**
4. **Run:** `./start-client.sh`
5. **Open:** http://localhost:9090

### **🪟 Windows Users:**
1. **Install Docker Desktop for Windows**
2. **Extract this package to a folder**
3. **Double-click:** `start-client.bat`
4. **Open:** http://localhost:9090

## 🔑 **Pre-configured Access**

### **Admin User:**
- **Email:** `admin@gmail.com`
- **Password:** `admin123`
- **Role:** ADMIN (full access)

### **System Access:**
- **Main App:** http://localhost:9090
- **Database Admin:** http://localhost:8082

## ✨ **Latest Features Included**

### **🎯 Core Features:**
- ✅ **Purchase Management** - Add, edit, manage car purchases
- ✅ **Client Management** - Track client accounts and balances
- ✅ **Booking System** - Create shipping schedules
- ✅ **FOB/C&F Calculations** - Advanced cost calculations
- ✅ **Rixo Request Generator** - Generate PDF transport requests
- ✅ **User Management** - Role-based access control

### **🆕 Recent Updates:**
- ✅ **BOOKING DETAILS Page** - Checkbox selection (C&F/FOB) with Calculate button
- ✅ **FOB Page** - Saves to `total_cnf_price` column, navigates to BOOKING DETAILS
- ✅ **Database Schema** - Removed `total_fob_price` column, optimized structure
- ✅ **PDF Generation** - Fixed alignment issues, proper Japanese formatting
- ✅ **UI Improvements** - Better styling, responsive design

## 🛠️ **System Requirements**

### **Minimum Requirements:**
- **Docker Desktop** (latest version)
- **8GB RAM** (16GB recommended)
- **10GB free disk space**
- **Internet connection** (for initial setup)

### **Supported Platforms:**
- ✅ **MacBook** (Intel and Apple Silicon)
- ✅ **Windows** (10/11)
- ✅ **Linux** (Ubuntu, CentOS, etc.)

## 📋 **Client Instructions**

### **For MacBook Users:**
```bash
# 1. Install Docker Desktop for Mac
# 2. Extract package and open Terminal
cd /path/to/automan-system

# 3. Start the system
./start-client.sh

# 4. Open browser to http://localhost:9090
# 5. Login with admin@gmail.com / admin123
```

### **For Windows Users:**
```cmd
REM 1. Install Docker Desktop for Windows
REM 2. Extract package and open Command Prompt
cd C:\path\to\automan-system

REM 3. Start the system
start-client.bat

REM 4. Open browser to http://localhost:9090
REM 5. Login with admin@gmail.com / admin123
```

## 🔧 **System Management**

### **Start System:**
```bash
# MacBook/Linux
./start-client.sh

# Windows
start-client.bat
```

### **Stop System:**
```bash
docker-compose -f docker-compose.client.yml down
```

### **Restart System:**
```bash
docker-compose -f docker-compose.client.yml restart
```

### **View Logs:**
```bash
docker-compose -f docker-compose.client.yml logs
```

## 🎯 **Key Features to Demonstrate**

### **1. Purchase Management**
- Add new car purchases with all details
- Edit existing purchases
- Filter and search purchases
- Export purchase data

### **2. Client Management**
- View client accounts and balances
- Track payment history
- Add new clients
- Monitor credit limits

### **3. Booking System**
- Create shipping schedules
- Calculate FOB/C&F costs
- Generate booking documents
- Track vessel information

### **4. Rixo Request Generator**
- Select cars for Rixo transport
- Generate PDF requests
- Track Rixo request status
- Export transport documents

## 🆘 **Troubleshooting**

### **System Won't Start:**
1. Ensure Docker Desktop is running
2. Check system resources (RAM/disk space)
3. Restart Docker Desktop
4. Run: `docker-compose -f docker-compose.client.yml logs`

### **Can't Access Application:**
1. Wait 2-3 minutes for all services to start
2. Check if all containers are running
3. Try accessing: http://localhost:9090
4. Check browser console for errors

### **Performance Issues:**
1. Allocate more RAM to Docker (8GB+)
2. Close other applications
3. Restart Docker Desktop
4. Check system resources

## 📞 **Support Information**

### **System Status:**
- **Backend API:** http://localhost:8083/api
- **Database:** http://localhost:8082 (phpMyAdmin)
- **Frontend:** http://localhost:9090

### **Default Credentials:**
- **Database:** automan_user / automan_password
- **Admin User:** admin@gmail.com / admin123

### **Log Locations:**
- **System Logs:** `docker-compose -f docker-compose.client.yml logs`
- **Backend Logs:** `docker logs automan_backend_client`
- **Frontend Logs:** `docker logs automan_frontend_client`

---

## 🎉 **Ready for Client Delivery!**

This package contains a complete, production-ready Automan Car Purchase Management System with:
- ✅ All latest features and updates
- ✅ Pre-configured admin user
- ✅ Cross-platform compatibility
- ✅ Easy setup and deployment
- ✅ Comprehensive documentation

**Your clients can now test the complete system immediately!** 🚀
