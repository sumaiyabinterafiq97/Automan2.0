#!/usr/bin/env bash
# Start the full multiplatform stack: MySQL -> backend (when healthy) -> frontend (when backend healthy) -> phpMyAdmin.
# Use this when you see nginx 502 on /api/* or phpMyAdmin cannot resolve host "mysql".

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.multiplatform.yml"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-automan_local}"

cd "$PROJECT_ROOT"
echo "Starting full stack (project: $COMPOSE_PROJECT_NAME)..."

# Stop/remove this compose project (volumes are kept). Clears stale container IDs.
docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true

# If a stray container still holds a fixed container_name from an old run, free the name.
for name in automan_mysql_multiplatform automan_backend_multiplatform automan_frontend_multiplatform automan_phpmyadmin_multiplatform; do
  docker rm -f "$name" 2>/dev/null || true
done

docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" up -d

echo ""
echo "Status:"
docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" ps

echo ""
echo "Frontend:    http://localhost:8080"
echo "phpMyAdmin:  http://localhost:8082"
echo "Backend:     http://localhost:8083/api/actuator/health"
echo ""
echo "If backend stays unhealthy, check: docker logs automan_backend_multiplatform"
