# 🚗 Automan Car Purchase System - Windows Compatible Version

## 📦 **Windows-Optimized Docker Images**

This package includes the complete Automan Car Purchase Management System with **Windows-compatible Docker images** built specifically for `linux/amd64` architecture.

### **🎯 What's Included**

- **Windows-Compatible Images**: Built for Intel/AMD processors (linux/amd64)
- **Complete System**: Frontend, Backend, Database with Flyway migrations
- **Pre-configured Data**: Admin user, sample client, and purchase data
- **Professional Database Management**: Version-controlled schema migrations
- **No Platform Issues**: Optimized for Windows systems

### **🚀 Super Simple Setup (3 Steps)**

#### **Step 1: Install Docker Desktop**
- **Windows**: Download from https://www.docker.com/products/docker-desktop/
- **Start Docker Desktop** and wait for it to fully load

#### **Step 2: Start the System**
**Double-click `start.bat`** OR **run in Command Prompt:**
```cmd
start.bat
```

#### **Step 3: Access the Application**
1. **Open browser**: http://localhost:8080
2. **Login**: admin@automan.com / admin123
3. **Start using!** 🎉

### **✨ What You Get**

#### **🎯 Complete System:**
- **Frontend**: Kotlin/JS (Compose for Web)
- **Backend**: Spring Boot (Kotlin) with Flyway migrations
- **Database**: MySQL 8.0 with automatic schema setup
- **Professional Database Management**: Version-controlled migrations

#### **🔑 Pre-configured Access**

**Admin User:**
- **Email**: `admin@automan.com`
- **Password**: `admin123`
- **Role**: ADMIN (full system access)

**System Access Points:**
- **Main Application**: http://localhost:8080
- **Backend API**: http://localhost:8083/api
- **MySQL Direct**: localhost:3306

#### **📊 Pre-populated Data (via Flyway migrations):**
- **1 Admin user** (admin@automan.com)
- **1 Client** (Tokyo Auto Import)
- **4 Sample purchases** with realistic data
- **Sample events and vessels**
- **Automatic database schema setup**

### **🛠️ System Management**

#### **Start System:**
```cmd
docker-compose up -d
```

#### **Stop System:**
```cmd
docker-compose down
```

#### **Restart System:**
```cmd
docker-compose restart
```

#### **View Logs:**
```cmd
docker-compose logs -f
```

#### **Complete Cleanup:**
```cmd
docker-compose down -v
docker system prune -a -f
```

### **🎯 Windows Compatibility Benefits**

#### **✅ Optimized for Windows:**
- **Built for linux/amd64**: Perfect for Intel/AMD processors
- **No Platform Mismatch**: No more architecture conflicts
- **Faster Loading**: Optimized for Windows Docker Desktop
- **Better Performance**: Native Windows compatibility

#### **✅ Professional Database Management:**
- **Version Control**: Database schema changes are tracked
- **Automatic Migrations**: Schema updates happen automatically
- **Rollback Capability**: Safe database changes with rollback
- **Consistent Schema**: Same database structure everywhere
- **Production Ready**: Professional database version control

### **🌐 Compatibility**

#### **Supported Platforms:**
- ✅ **Windows** (10/11) - Intel/AMD processors
- ✅ **Windows Server** (2019/2022)
- ✅ **WSL2** (Windows Subsystem for Linux)

#### **Requirements:**
- Docker Desktop installed and running
- 4GB+ RAM available
- 10GB+ free disk space
- Windows 10/11 or Windows Server 2019/2022

### **❓ Troubleshooting**

#### **"Docker is not running"**
- Start Docker Desktop and wait for it to fully load
- Ensure Docker Desktop is running in the system tray

#### **"Failed to load images"**
- Ensure `automan-windows-compatible.tar` is in the same directory
- Check available disk space (needs ~850MB)

#### **"Port already in use"**
- Stop other applications using ports 8080, 8083, or 3306
- Or run: `docker-compose down` to stop existing containers

#### **"Login failed"**
- Use the correct credentials: `admin@automan.com` / `admin123`
- Wait for the system to fully start (may take 1-2 minutes)

### **📞 Support**

If you encounter any issues:
1. Check the logs: `docker-compose logs`
2. Ensure Docker Desktop is running
3. Try a complete restart: `docker-compose down -v && docker-compose up -d`

---

**🎉 Enjoy your Windows-optimized Automan Car Purchase Management System!**
