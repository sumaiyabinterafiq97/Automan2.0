# 🚗 Automan Car Purchase Management System - Client Setup Guide

## 📋 **System Overview**
This is a complete car purchase management system with the following features:
- **Purchase Management**: Add, edit, and manage car purchases
- **Client Management**: Track client accounts and balances
- **Booking System**: Create shipping schedules and calculate costs
- **FOB/C&F Calculations**: Advanced cost calculation tools
- **Rixo Request Generator**: Generate PDF requests for Rixo transport
- **User Management**: Role-based access control

## 🔑 **Pre-configured Admin Access**
- **Email**: `admin@gmail.com`
- **Password**: `admin123`
- **Role**: ADMIN (full access to all features)

## 🖥️ **System Requirements**
- **Docker Desktop** (latest version)
- **8GB RAM** minimum (16GB recommended)
- **10GB free disk space**
- **Internet connection** for initial setup

## 🚀 **Quick Start (5 Minutes)**

### **Step 1: Download and Extract**
1. Extract the provided files to a folder (e.g., `automan-system`)
2. Open terminal/command prompt in that folder

### **Step 2: Start the System**
```bash
# For MacBook/Linux:
docker-compose -f docker-compose.client.yml up -d

# For Windows:
docker-compose -f docker-compose.client.yml up -d
```

### **Step 3: Access the Application**
- **Main Application**: http://localhost:9090
- **Database Admin**: http://localhost:8082 (phpMyAdmin)
- **Login**: admin@gmail.com / admin123

## 📱 **Platform-Specific Instructions**

### **🍎 MacBook Users**
1. **Install Docker Desktop for Mac**
   - Download from: https://www.docker.com/products/docker-desktop/
   - Install and start Docker Desktop
   - Ensure Docker is running (whale icon in menu bar)

2. **Open Terminal**
   - Press `Cmd + Space`, type "Terminal", press Enter
   - Navigate to the extracted folder: `cd /path/to/automan-system`

3. **Start the System**
   ```bash
   docker-compose -f docker-compose.client.yml up -d
   ```

### **🪟 Windows Users**
1. **Install Docker Desktop for Windows**
   - Download from: https://www.docker.com/products/docker-desktop/
   - Install and restart your computer
   - Start Docker Desktop

2. **Open Command Prompt or PowerShell**
   - Press `Win + R`, type "cmd", press Enter
   - Navigate to the extracted folder: `cd C:\path\to\automan-system`

3. **Start the System**
   ```cmd
   docker-compose -f docker-compose.client.yml up -d
   ```

## 🔧 **System Management**

### **Start the System**
```bash
docker-compose -f docker-compose.client.yml up -d
```

### **Stop the System**
```bash
docker-compose -f docker-compose.client.yml down
```

### **Restart the System**
```bash
docker-compose -f docker-compose.client.yml restart
```

### **View System Status**
```bash
docker-compose -f docker-compose.client.yml ps
```

### **View System Logs**
```bash
# All services
docker-compose -f docker-compose.client.yml logs

# Specific service
docker-compose -f docker-compose.client.yml logs frontend
docker-compose -f docker-compose.client.yml logs backend
```

## 🌐 **Access Points**

| Service | URL | Purpose |
|---------|-----|---------|
| **Main App** | http://localhost:9090 | Primary application interface |
| **Database Admin** | http://localhost:8082 | phpMyAdmin for database management |
| **Backend API** | http://localhost:8083/api | REST API endpoints |

## 👤 **User Accounts**

### **Admin User (Pre-configured)**
- **Email**: admin@gmail.com
- **Password**: admin123
- **Role**: ADMIN
- **Access**: Full system access

### **Creating Additional Users**
1. Login as admin
2. Go to "User Management" page
3. Click "Add New User"
4. Fill in user details and assign role

## 🎯 **Key Features to Test**

### **1. Purchase Management**
- Add new car purchases
- Edit existing purchases
- View purchase list with filtering

### **2. Client Management**
- View client accounts and balances
- Add new clients
- Track payment history

### **3. Booking System**
- Create shipping schedules
- Calculate FOB/C&F costs
- Generate booking documents

### **4. Rixo Request Generator**
- Select cars for Rixo transport
- Generate PDF requests
- Track Rixo request status

## 🛠️ **Troubleshooting**

### **System Won't Start**
```bash
# Check if Docker is running
docker --version

# Check system status
docker-compose -f docker-compose.client.yml ps

# View error logs
docker-compose -f docker-compose.client.yml logs
```

### **Can't Access Application**
1. Wait 2-3 minutes for all services to start
2. Check if all containers are running: `docker-compose -f docker-compose.client.yml ps`
3. Try accessing: http://localhost:9090

### **Database Connection Issues**
1. Check if MySQL container is running
2. Access phpMyAdmin: http://localhost:8082
3. Login with: automan_user / automan_password

### **Performance Issues**
- Ensure Docker has at least 8GB RAM allocated
- Close other applications to free up resources
- Restart Docker Desktop if needed

## 📞 **Support**

If you encounter any issues:
1. Check the troubleshooting section above
2. View system logs: `docker-compose -f docker-compose.client.yml logs`
3. Contact support with error details

## 🔄 **Updates**

To update the system:
1. Stop the system: `docker-compose -f docker-compose.client.yml down`
2. Replace files with new versions
3. Start the system: `docker-compose -f docker-compose.client.yml up -d`

---

**🎉 Enjoy using the Automan Car Purchase Management System!**
