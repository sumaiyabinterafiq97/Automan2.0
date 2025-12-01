# Complete Docker Cleanup Script for Windows
# Run this in PowerShell as Administrator

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Docker Desktop Complete Cleanup - Windows" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Stop all running containers
Write-Host "[1/7] Stopping all running containers..." -ForegroundColor Yellow
docker stop $(docker ps -aq) 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] All containers stopped" -ForegroundColor Green
} else {
    Write-Host "[INFO] No running containers to stop" -ForegroundColor Gray
}

# Step 2: Remove all containers (stopped and running)
Write-Host "[2/7] Removing all containers..." -ForegroundColor Yellow
docker rm -f $(docker ps -aq) 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] All containers removed" -ForegroundColor Green
} else {
    Write-Host "[INFO] No containers to remove" -ForegroundColor Gray
}

# Step 3: Remove all images
Write-Host "[3/7] Removing all Docker images..." -ForegroundColor Yellow
docker rmi -f $(docker images -aq) 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] All images removed" -ForegroundColor Green
} else {
    Write-Host "[INFO] No images to remove" -ForegroundColor Gray
}

# Step 4: Remove all volumes
Write-Host "[4/7] Removing all Docker volumes..." -ForegroundColor Yellow
docker volume rm $(docker volume ls -q) 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] All volumes removed" -ForegroundColor Green
} else {
    Write-Host "[INFO] No volumes to remove" -ForegroundColor Gray
}

# Step 5: Remove all networks (except default)
Write-Host "[5/7] Removing all custom networks..." -ForegroundColor Yellow
docker network prune -f
Write-Host "[OK] All custom networks removed" -ForegroundColor Green

# Step 6: Prune everything (system-wide cleanup)
Write-Host "[6/7] Pruning Docker system (images, containers, networks, build cache)..." -ForegroundColor Yellow
docker system prune -a -f --volumes
Write-Host "[OK] System pruned" -ForegroundColor Green

# Step 7: Verify cleanup
Write-Host "[7/7] Verifying cleanup..." -ForegroundColor Yellow
$containers = docker ps -aq
$images = docker images -q
$volumes = docker volume ls -q
$networks = docker network ls -q

Write-Host ""
Write-Host "Cleanup Summary:" -ForegroundColor Cyan
Write-Host "  Containers remaining: $($containers.Count)" -ForegroundColor $(if ($containers.Count -eq 0) { "Green" } else { "Yellow" })
Write-Host "  Images remaining: $($images.Count)" -ForegroundColor $(if ($images.Count -eq 0) { "Green" } else { "Yellow" })
Write-Host "  Volumes remaining: $($volumes.Count)" -ForegroundColor $(if ($volumes.Count -eq 0) { "Green" } else { "Yellow" })
Write-Host "  Networks remaining: $($networks.Count)" -ForegroundColor $(if ($networks.Count -le 3) { "Green" } else { "Yellow" })

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Docker cleanup completed!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Load the new Docker image: docker load -i automan-multiplatform-client-final-fixed-corrected.tar" -ForegroundColor White
Write-Host "  2. Tag the images: docker tag automan20-backend:amd64 automan20-backend:latest" -ForegroundColor White
Write-Host "  3. Tag the images: docker tag automan20-frontend:amd64 automan20-frontend:latest" -ForegroundColor White
Write-Host "  4. Start the system: docker-compose up -d" -ForegroundColor White
Write-Host ""

