# 🐳 Automan Client Docker Guide

## 📦 Single Docker Image Delivery

This guide explains how to deliver and run the Automan application as a single Docker image for your client.

## 🚀 For You (Developer)

### Building the Client Image

```bash
# Build the single Docker image
./build-client-image.sh

# Or manually:
docker build -f Dockerfile.client -t automan-client:latest .
```

### Testing the Image

```bash
# Test the image locally
docker run -p 8080:8080 automan-client:latest

# Access the application at: http://localhost:8080
```

### Creating Client Package

```bash
# Save image as tar file for client delivery
docker save automan-client:latest -o automan-client.tar

# Compress for smaller file size
gzip automan-client.tar
```

## 📤 For Your Client

### Prerequisites

Your client needs:
- Docker installed on their system
- Internet connection (for initial Docker setup)

### Installation Steps

1. **Receive the Docker image file:**
   - `automan-client.tar.gz` (compressed)
   - Or `automan-client.tar` (uncompressed)

2. **Load the Docker image:**
   ```bash
   # If compressed:
   gunzip automan-client.tar.gz
   docker load -i automan-client.tar
   
   # If uncompressed:
   docker load -i automan-client.tar
   ```

3. **Run the application:**
   ```bash
   docker run -p 8080:8080 automan-client:latest
   ```

4. **Access the application:**
   - Open browser: `http://localhost:8080`
   - The application will be fully functional

### Client Commands

```bash
# Start the application
docker run -p 8080:8080 automan-client:latest

# Run in background (detached)
docker run -d -p 8080:8080 --name automan automan-client:latest

# Stop the application
docker stop automan

# Remove the container
docker rm automan

# View logs
docker logs automan
```

## 🔧 What's Included

The single Docker image contains:
- ✅ **Frontend**: Kotlin/JS web application
- ✅ **Backend**: Spring Boot API server
- ✅ **Database**: MySQL with initial data
- ✅ **Web Server**: Nginx for serving frontend
- ✅ **Process Manager**: Supervisor for managing services

## 🌐 Application Access

- **Main Application**: http://localhost:8080
- **API Endpoints**: http://localhost:8080/api/
- **Database**: Internal (MySQL running inside container)

## 📊 Data Persistence

**Important**: By default, data is stored inside the container and will be lost when the container is removed.

### For Data Persistence (Optional)

```bash
# Run with data persistence
docker run -d -p 8080:8080 \
  -v automan_data:/var/lib/mysql \
  --name automan \
  automan-client:latest
```

## 🛠️ Troubleshooting

### Common Issues

1. **Port 8080 already in use:**
   ```bash
   # Use different port
   docker run -p 8081:8080 automan-client:latest
   ```

2. **Application not starting:**
   ```bash
   # Check logs
   docker logs automan
   ```

3. **Database connection issues:**
   ```bash
   # Wait a bit longer for services to start
   # The application takes 30-60 seconds to fully start
   ```

### Health Check

The application includes a health check that verifies:
- Frontend is accessible
- All services are running
- Database is connected

## 📋 System Requirements

- **Docker**: Version 20.10 or higher
- **RAM**: Minimum 2GB, Recommended 4GB
- **Disk Space**: 2GB for the image
- **OS**: Windows, macOS, or Linux

## 🎯 Benefits of Single Image Approach

✅ **Simple**: One command to run everything  
✅ **Self-contained**: No external dependencies  
✅ **Portable**: Works on any system with Docker  
✅ **Easy to deploy**: Just load and run  
✅ **No configuration**: Everything pre-configured  

## ⚠️ Limitations

- **Data persistence**: Requires volume mounting for production
- **Scalability**: Single container approach
- **Resource usage**: All services in one container
- **Updates**: Requires rebuilding the entire image

## 🔄 Updates

To update the application:
1. Receive new image file from developer
2. Stop current container: `docker stop automan`
3. Load new image: `docker load -i new-automan-client.tar`
4. Run new image: `docker run -p 8080:8080 automan-client:latest`

---

**Need Help?** Contact your developer for support.
