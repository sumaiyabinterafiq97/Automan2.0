# 🚗 Automan Car Purchase System - Flyway Migration Version

## 📦 **Professional Database Management**

This package includes the complete Automan Car Purchase Management System with **Flyway Migration** for professional database version control.

### **🎯 What's Included**

- **Complete System**: Frontend, Backend, Database with Flyway migrations
- **Pre-configured Data**: Admin user, sample client, and purchase data
- **Professional Database Management**: Version-controlled schema migrations
- **Cross-Platform**: Works on MacBook, Windows, and Linux

### **🚀 Super Simple Setup (3 Steps)**

#### **Step 1: Install Docker Desktop**
- **MacBook**: Download from https://www.docker.com/products/docker-desktop/
- **Windows**: Download from https://www.docker.com/products/docker-desktop/
- **Start Docker Desktop** and wait for it to fully load

#### **Step 2: Start the System**

**🍎 MacBook/Linux:**
```bash
# Open Terminal in this folder
./start.sh
```

**🪟 Windows:**
```cmd
# Double-click or run in Command Prompt
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
```bash
docker-compose up -d
```

#### **Stop System:**
```bash
docker-compose down
```

#### **Restart System:**
```bash
docker-compose restart
```

#### **View Logs:**
```bash
docker-compose logs -f
```

#### **Complete Cleanup:**
```bash
docker-compose down -v
docker system prune -a -f
```

### **🎯 Flyway Migration Benefits**

#### **✅ Professional Database Management:**
- **Version Control**: Database schema changes are tracked
- **Automatic Migrations**: Schema updates happen automatically
- **Rollback Capability**: Safe database changes with rollback
- **Consistent Schema**: Same database structure everywhere
- **Production Ready**: Professional database version control

#### **✅ No More Manual SQL Files:**
- **Automatic Setup**: Database schema created automatically
- **Data Seeding**: Sample data loaded automatically
- **Version Tracking**: All changes are version controlled
- **Safe Updates**: Migrations are safe and reversible

### **🌐 Compatibility**

#### **Supported Platforms:**
- ✅ **MacBook** (Intel and Apple Silicon)
- ✅ **Windows** (10/11)
- ✅ **Linux** (Ubuntu, CentOS, etc.)

#### **Requirements:**
- Docker Desktop installed and running
- 4GB+ RAM available
- 10GB+ free disk space

### **❓ Troubleshooting**

#### **"Docker is not running"**
- Start Docker Desktop and wait for it to fully load
- Ensure Docker Desktop is running in the system tray

#### **"Failed to load images"**
- Ensure `automan-complete-flyway.tar` is in the same directory
- Check available disk space (needs ~1.1GB)

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

**🎉 Enjoy your professional Automan Car Purchase Management System with Flyway Migration!**
