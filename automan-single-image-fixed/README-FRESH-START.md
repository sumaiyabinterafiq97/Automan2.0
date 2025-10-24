# 🚗 Automan Car Purchase Management System - FRESH START Package

## 🎯 **FRESH START VERSION - No Pre-configured Users**

This package contains a **completely fresh system** with no pre-configured admin users. Clients will create their own admin account during the first setup.

### **✅ What's Different:**
- **No default admin user** - clients create their own
- **No password hash issues** - clients set their own password
- **Full control** over admin account creation
- **Clean database** with sample data only

## 📦 **What's Included**

This package contains a **single Docker image file** with the complete Automan Car Purchase Management System:

### **🐳 Single Docker Image:**
- `automan-complete-fresh.tar` - Complete system with backend, frontend, and database
- Contains all latest features and updates
- **NO pre-configured users** - fresh start
- Windows and MacBook compatible ports

### **📋 Configuration Files:**
- `docker-compose.client.yml` - System configuration
- `database/init.sql` - Database with sample data only (no users)

### **🚀 Setup Scripts:**
- `load-and-run-fresh.sh` - MacBook/Linux setup script
- `load-and-run-fresh.bat` - Windows setup script

## 🚀 **Super Simple Setup (3 Steps)**

### **Step 1: Install Docker Desktop**
- **MacBook**: Download from https://www.docker.com/products/docker-desktop/
- **Windows**: Download from https://www.docker.com/products/docker-desktop/
- **Start Docker Desktop** and wait for it to fully load

### **Step 2: Load and Start System**

#### **🍎 MacBook/Linux:**
```bash
# Open Terminal in this folder
./load-and-run-fresh.sh
```

#### **🪟 Windows:**
```cmd
# Double-click or run in Command Prompt
load-and-run-fresh.bat
```

### **Step 3: First-Time Setup**
1. **Open browser**: http://localhost:8003
2. **You'll see the Sign Up page** (no login required)
3. **Create your admin account**:
   - **Name**: Your name
   - **Email**: Your email address
   - **Password**: Your preferred password
4. **Click "Sign Up"** to create your admin account
5. **Start using the system!** 🎉

## ✨ **What You Get**

### **🎯 Complete System:**
- **Car Purchase Management** - Track purchases, clients, and transactions
- **Rixo Request Generator** - Generate transport requests
- **Booking System** - Manage car bookings and shipments
- **Client Management** - Track client balances and transactions
- **PDF Generation** - Export reports and documents
- **User Management** - Create and manage user accounts

### **📊 Sample Data Included:**
- **Sample purchases** with Toyota vehicles
- **Sample clients** (Crown Eagle, Tokyo Auto, Nagoya Motors)
- **Sample transactions** and events
- **Sample vessels** for booking system

## 🔑 **First-Time Setup Process**

### **1. System Startup:**
- All services start automatically
- Database initializes with sample data
- No users exist initially

### **2. First User Creation:**
- Navigate to http://localhost:8003
- System detects no users exist
- Shows Sign Up page instead of Login
- Create your admin account with your preferred credentials

### **3. Admin Account:**
- **First user automatically becomes ADMIN**
- **Full system access** granted
- **Can create additional users** later

## 🌐 **System Access Points**

### **Main Application:**
- **URL**: http://localhost:8003
- **Purpose**: Main application interface

### **Database Administration:**
- **URL**: http://localhost:8004
- **Purpose**: phpMyAdmin for database management
- **Credentials**: automan_user / automan_password

### **Backend API:**
- **URL**: http://localhost:8002/api
- **Purpose**: REST API endpoints

### **MySQL Direct Access:**
- **Host**: localhost
- **Port**: 8001
- **Database**: automan_car_purchase
- **Credentials**: automan_user / automan_password

## 🛠️ **System Management**

### **Start System:**
```bash
# MacBook/Linux
./load-and-run-fresh.sh

# Windows
load-and-run-fresh.bat
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

## 🎯 **Advantages of Fresh Start Package**

### **✅ No Authentication Issues:**
- **No password hash problems**
- **No pre-configured user conflicts**
- **Clients control their own credentials**

### **✅ Easy Setup:**
- **One-time setup process**
- **Clear first-time user experience**
- **No confusing login credentials**

### **✅ Full Control:**
- **Clients create their own admin**
- **Choose their own password**
- **Full system ownership**

### **✅ Cross-Platform:**
- **Windows-friendly ports** (8001-8004)
- **MacBook compatible**
- **No port conflicts**

## 🔧 **Troubleshooting**

### **If System Won't Start:**
1. **Check Docker Desktop** is running
2. **Clean up old containers**:
   ```bash
   docker-compose -f docker-compose.client.yml down
   docker system prune -f
   ```
3. **Restart Docker Desktop**
4. **Try again**

### **If Ports Are Busy:**
- **Windows**: May need to run as Administrator
- **MacBook**: Check if other services are using ports 8001-8004

### **If Database Issues:**
- **Access phpMyAdmin**: http://localhost:8004
- **Check database**: automan_car_purchase
- **Verify tables**: users, purchases, clients, events

## 📋 **Supported Platforms**

### **Operating Systems:**
- ✅ **MacBook** (Intel and Apple Silicon)
- ✅ **Windows** (10/11)
- ✅ **Linux** (Ubuntu, CentOS, etc.)

### **Requirements:**
- **Docker Desktop** (latest version)
- **8GB RAM** minimum
- **10GB free disk space**

## 🎉 **Ready to Use!**

This fresh start package eliminates all the authentication issues we've been having. Clients get a clean system where they create their own admin account with their preferred credentials.

**No more password hash problems!** 🚀
