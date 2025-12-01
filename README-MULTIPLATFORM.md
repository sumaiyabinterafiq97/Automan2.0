# 🚀 Automan Car Purchase Management - Multiplatform Docker

A complete multiplatform Docker solution for the Automan Car Purchase Management system that works on both **Mac (Apple Silicon/ARM64)** and **Windows/Intel (AMD64)** platforms.

## 📋 Features

- ✅ **Multiplatform Support**: Works on Mac (ARM64) and Windows/Intel (AMD64)
- ✅ **Pre-populated Database**: Includes admin user, client, and sample purchases
- ✅ **Latest Code**: Includes role selection dropdown and all recent features
- ✅ **Single Image**: One Docker image for all platforms
- ✅ **Easy Setup**: Simple commands to get started

## 🗄️ Pre-populated Data

The Docker image comes with the following pre-populated data:

### 👤 Admin User
- **Email**: `admin@automan.com`
- **Password**: `admin123`
- **Role**: `ADMIN`

### 🏢 Client
- **Name**: CROWN EAGLE
- **Client Number**: CL001
- **Status**: ACTIVE
- **Balance**: ¥50,000

### 🚗 Sample Purchases (3 vehicles)
1. **NISSAN NV 150 AD** (2012) - ¥138,000
2. **TOYOTA VELLFIRE** (2020) - ¥460,000  
3. **TOYOTA HARRIER** (2021) - ¥1,516,000

## 🚀 Quick Start

### Option 1: Simple Local Build (Recommended for Testing)

```bash
# Build the image locally
docker build -f Dockerfile.simple -t automan:latest .

# Run with external database
docker run -p 9090:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/automan_car_purchase?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC \
  -e SPRING_DATASOURCE_USERNAME=automan_user \
  -e SPRING_DATASOURCE_PASSWORD=automan_password \
  automan:latest
```

### Option 2: Full Stack with Database

```bash
# Start the complete stack
docker-compose -f docker-compose.multiplatform.yml up -d

# Access the application
open http://localhost:9090
```

### Option 3: Multiplatform Build (For Distribution)

```bash
# Make the build script executable
chmod +x build-multiplatform.sh

# Build for both platforms
./build-multiplatform.sh
```

## 🔧 Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/automan_car_purchase?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | Database connection URL |
| `SPRING_DATASOURCE_USERNAME` | `automan_user` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `automan_password` | Database password |
| `SERVER_PORT` | `8083` | Backend server port |

### Ports

| Service | Port | Description |
|---------|------|-------------|
| Application | `9090` | Main application (frontend + backend) |
| Database | `3307` | MySQL database |
| phpMyAdmin | `8082` | Database management interface |

## 📁 File Structure

```
├── Dockerfile.simple              # Simple single-platform build
├── Dockerfile.multiplatform      # Multiplatform build (requires buildx)
├── docker-compose.multiplatform.yml  # Full stack with database
├── build-multiplatform.sh        # Build script for multiplatform
├── backend/src/main/kotlin/com/automan/backend/service/DataSeederService.kt  # Database seeding
└── README-MULTIPLATFORM.md       # This file
```

## 🛠️ Development

### Building for Development

```bash
# Build frontend
./gradlew jsBrowserProductionWebpack

# Build backend
cd backend && ./gradlew build -x test

# Build Docker image
docker build -f Dockerfile.simple -t automan:dev .
```

### Database Seeding

The `DataSeederService` automatically populates the database with:
- 1 Admin user
- 1 Client (CROWN EAGLE)
- 3 Sample purchases
- 2 Vessels
- 3 Rixo prices

## 🐳 Docker Commands

### Basic Commands

```bash
# Build image
docker build -f Dockerfile.simple -t automan:latest .

# Run container
docker run -p 9090:8080 automan:latest

# Run with environment variables
docker run -p 9090:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://your-db-host:3306/automan_car_purchase \
  -e SPRING_DATASOURCE_USERNAME=your-username \
  -e SPRING_DATASOURCE_PASSWORD=your-password \
  automan:latest

# View logs
docker logs <container-id>

# Stop container
docker stop <container-id>
```

### Full Stack Commands

```bash
# Start all services
docker-compose -f docker-compose.multiplatform.yml up -d

# View logs
docker-compose -f docker-compose.multiplatform.yml logs -f

# Stop all services
docker-compose -f docker-compose.multiplatform.yml down

# Rebuild and start
docker-compose -f docker-compose.multiplatform.yml up --build -d
```

## 🔍 Troubleshooting

### Common Issues

1. **Port Already in Use**
   ```bash
   # Check what's using the port
   lsof -i :9090
   
   # Kill the process
   kill -9 <PID>
   ```

2. **Database Connection Issues**
   ```bash
   # Check if MySQL is running
   docker ps | grep mysql
   
   # Check database logs
   docker logs automan_mysql
   ```

3. **Build Issues**
   ```bash
   # Clean Docker cache
   docker system prune -a
   
   # Rebuild without cache
   docker build --no-cache -f Dockerfile.simple -t automan:latest .
   ```

### Health Checks

```bash
# Check application health
curl http://localhost:9090/health

# Check database connection
docker exec automan_mysql mysql -u automan_user -pautoman_password -e "SELECT 1"
```

## 📊 Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| **Application** | http://localhost:9090 | admin@automan.com / admin123 |
| **phpMyAdmin** | http://localhost:8082 | automan_user / automan_password |
| **Database** | localhost:3307 | automan_user / automan_password |

## 🎯 Platform Support

| Platform | Architecture | Status |
|----------|-------------|--------|
| **Mac (Apple Silicon)** | ARM64 | ✅ Supported |
| **Mac (Intel)** | AMD64 | ✅ Supported |
| **Windows** | AMD64 | ✅ Supported |
| **Linux** | AMD64/ARM64 | ✅ Supported |

## 📝 Notes

- The application includes the latest role selection feature
- Database is automatically seeded on first run
- All data persists in Docker volumes
- The image is optimized for production use
- Health checks ensure service reliability

## 🤝 Support

For issues or questions:
1. Check the troubleshooting section above
2. Review Docker logs: `docker logs <container-name>`
3. Ensure all required ports are available
4. Verify database connectivity

---

**Ready to use!** 🚀 Your multiplatform Automan Car Purchase Management system is ready to deploy on any platform.
