# Complete Docker Cleanup Instructions for Windows

## Quick Commands (Run in PowerShell)

### Option 1: Run All Commands Separately (Recommended)

```powershell
# 1. Stop all running containers
docker stop $(docker ps -aq)

# 2. Remove all containers (force remove)
docker rm -f $(docker ps -aq)

# 3. Remove all images (force remove)
docker rmi -f $(docker images -aq)

# 4. Remove all volumes
docker volume rm $(docker volume ls -q)

# 5. Remove all custom networks
docker network prune -f

# 6. Complete system prune (removes everything including build cache)
docker system prune -a -f --volumes
```

### Option 2: Use the Cleanup Script

1. Copy `clean-docker-windows.ps1` to the client's Windows machine
2. Open PowerShell as Administrator
3. Navigate to the script location
4. Run: `.\clean-docker-windows.ps1`

## Step-by-Step Manual Cleanup

### Step 1: Stop All Containers
```powershell
docker stop $(docker ps -aq)
```

### Step 2: Remove All Containers
```powershell
docker rm -f $(docker ps -aq)
```

### Step 3: Remove All Images
```powershell
docker rmi -f $(docker images -aq)
```

### Step 4: Remove All Volumes
```powershell
docker volume rm $(docker volume ls -q)
```

### Step 5: Remove All Networks
```powershell
docker network prune -f
```

### Step 6: Complete System Prune
```powershell
docker system prune -a -f --volumes
```

## After Cleanup - Load New Image

After cleaning Docker, load the new image:

```powershell
# Navigate to the folder with the tar file
cd "E:\11th semester\automan-multiplatform-client-final-fixed-corrected"

# Load the Docker image
docker load -i automan-multiplatform-client-final-fixed-corrected.tar

# Tag the images for Windows (AMD64)
docker tag automan20-backend:amd64 automan20-backend:latest
docker tag automan20-frontend:amd64 automan20-frontend:latest

# Start the system
docker-compose up -d
```

## Verify Cleanup

Check what's left in Docker:

```powershell
# Check containers
docker ps -a

# Check images
docker images

# Check volumes
docker volume ls

# Check networks
docker network ls
```

## Troubleshooting

If you get errors about containers/images/volumes in use:

1. **Force remove specific containers:**
   ```powershell
   docker rm -f automan_backend_multiplatform automan_frontend_multiplatform automan_mysql_multiplatform automan_phpmyadmin_multiplatform
   ```

2. **Force remove specific volumes:**
   ```powershell
   docker volume rm automan-multiplatform-client-final-fixed-corrected_mysql_data_multiplatform
   ```

3. **If volume is in use, find and remove the container first:**
   ```powershell
   docker inspect -f "{{.Name}}" <container_id>
   docker rm -f <container_id>
   docker volume rm <volume_name>
   ```

## Notes

- The `-f` flag forces removal even if containers are running
- The `-a` flag in `docker system prune` removes all unused images, not just dangling ones
- The `--volumes` flag also removes all unused volumes
- Some default networks (bridge, host, none) cannot be removed - this is normal

