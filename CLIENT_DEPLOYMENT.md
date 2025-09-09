# 🚀 Automan Application - Client Deployment Guide

## Prerequisites
- **Docker Desktop** installed on your computer
  - Download from: https://www.docker.com/products/docker-desktop/
  - Ensure Docker Desktop is running

## 🎯 Quick Start (One Command Deployment)

1. **Extract the application files** to a folder on your computer
2. **Open Terminal/Command Prompt** in that folder
3. **Run the deployment command:**
   ```bash
   # On Mac/Linux:
   chmod +x build-docker.sh
   ./build-docker.sh
   
   # On Windows:
   docker-compose up --build -d
   ```

## 🌐 Access Your Application

Once deployment is complete, open your web browser:

- **🌐 Main Application**: http://localhost:8080
- **🔧 API Documentation**: http://localhost:8083/api
- **🗄️ Database Admin**: http://localhost:8082
  - Username: `automan_user`
  - Password: `automan_password`

## 📱 Management Commands

### Start the Application
```bash
docker-compose up -d
```

### Stop the Application
```bash
docker-compose down
```

### View Application Logs
```bash
docker-compose logs -f
```

### Restart the Application
```bash
docker-compose restart
```

### Update to New Version
```bash
docker-compose down
docker-compose pull
docker-compose up -d
```

## 🔧 Troubleshooting

### Port Already in Use
If you get "port already in use" errors:
```bash
# Stop all containers
docker-compose down

# Check what's using the ports
lsof -i :8080
lsof -i :8083
lsof -i :8082
lsof -i :3307
```

### Database Connection Issues
```bash
# Check MySQL container status
docker-compose ps mysql

# View MySQL logs
docker-compose logs mysql
```

### Application Not Loading
```bash
# Check all service statuses
docker-compose ps

# View all logs
docker-compose logs
```

## 📊 System Requirements

- **RAM**: Minimum 4GB, Recommended 8GB
- **Storage**: Minimum 2GB free space
- **OS**: Windows 10/11, macOS 10.15+, or Linux
- **Docker**: Version 20.10+ (included with Docker Desktop)

## 🔒 Security Notes

- The application runs locally on your computer
- Database is accessible only from localhost
- Default passwords are for development - change for production use
- All data is stored locally in Docker volumes

## 📞 Support

If you encounter any issues:
1. Check the troubleshooting section above
2. Ensure Docker Desktop is running
3. Restart Docker Desktop if needed
4. Contact the development team with error logs

## 🎉 You're All Set!

Your Automan application is now running locally with Docker. Enjoy managing your car purchases!
