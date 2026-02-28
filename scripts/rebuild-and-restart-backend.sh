#!/usr/bin/env bash
# Rebuild backend image and restart backend container using Compose (keeps state in sync).
# Use this instead of: docker stop/rm + compose up (which can leave Compose with a stale container ID).

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.multiplatform.yml"

echo "🔨 Building backend image..."
docker build -t automan20-backend:latest -f "$PROJECT_ROOT/backend/Dockerfile" "$PROJECT_ROOT/backend/"

echo "🔄 Restarting backend..."
cd "$PROJECT_ROOT"
# Remove backend container by name so Compose doesn't use a stale container ID on next start
docker stop automan_backend_multiplatform 2>/dev/null || true
docker rm -f automan_backend_multiplatform 2>/dev/null || true
# Create new container via Compose, then start by name (avoids Compose v2 stale container ID bug)
docker compose -f "$COMPOSE_FILE" create backend
docker start automan_backend_multiplatform

echo "✅ Done. Backend should be running on port 8083."
