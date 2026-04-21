#!/usr/bin/env bash
# Rebuild backend image and restart backend container using Compose (keeps state in sync).
# Use this instead of: docker stop/rm + compose up (which can leave Compose with a stale container ID).

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.multiplatform.yml"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-automan_local}"

echo "🔨 Building backend image..."
docker build -t automan20-backend:latest -f "$PROJECT_ROOT/backend/Dockerfile" "$PROJECT_ROOT/backend/"

echo "🔄 Restarting backend..."
cd "$PROJECT_ROOT"
# If a backend container exists from a different Compose project, remove it
docker stop automan_backend_multiplatform 2>/dev/null || true
docker rm -f automan_backend_multiplatform 2>/dev/null || true
# Do not use --no-deps: backend depends on MySQL being healthy. Starting backend alone leaves DB down
# and causes 502 from nginx plus phpMyAdmin "mysql" host resolution failures.
docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" up -d --force-recreate backend

echo "✅ Done. Backend should be running on port 8083."
