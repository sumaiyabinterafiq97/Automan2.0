#!/usr/bin/env bash
# Rebuild backend image and restart the backend container.
# Uses create + start by name to avoid Docker Compose "No such container" bug
# (Compose sometimes references a stale container ID when using "up").

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.multiplatform.yml"

echo "Building backend image..."
docker build -t automan20-backend:latest -f "$PROJECT_ROOT/backend/Dockerfile" "$PROJECT_ROOT/backend/"

cd "$PROJECT_ROOT"

# Stop and remove existing backend container so create can make a fresh one
echo "Stopping and removing existing backend container..."
docker stop automan_backend_multiplatform 2>/dev/null || true
docker rm automan_backend_multiplatform 2>/dev/null || true

# Create the container (don't use 'up' - it has a bug with stale container ID)
echo "Creating backend container..."
docker compose -f "$COMPOSE_FILE" create backend

# Start by name (avoids Compose's broken start step)
echo "Starting backend container..."
docker start automan_backend_multiplatform

echo "Backend container status:"
docker ps --filter name=automan_backend_multiplatform
