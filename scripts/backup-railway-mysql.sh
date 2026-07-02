#!/usr/bin/env bash
# Export Railway MySQL to a local .sql.gz file (portable backup).
#
# Prerequisites:
#   brew install mysql-client   # macOS — provides mysqldump on PATH
#
# Setup:
#   cp .env.railway.backup.example .env.railway.backup
#   # Fill in values from Railway → MySQL service → Variables (+ public TCP proxy host/port)
#
# Usage:
#   ./scripts/backup-railway-mysql.sh
#   BACKUP_LABEL=pre-v57 ./scripts/backup-railway-mysql.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${ENV_FILE:-$PROJECT_ROOT/.env.railway.backup}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups/railway}"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ Missing $ENV_FILE"
  echo "   cp .env.railway.backup.example .env.railway.backup"
  echo "   Then add Railway MySQL credentials (public TCP proxy host/port)."
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

: "${RAILWAY_MYSQL_HOST:?Set RAILWAY_MYSQL_HOST in .env.railway.backup}"
: "${RAILWAY_MYSQL_PORT:?Set RAILWAY_MYSQL_PORT in .env.railway.backup}"
: "${RAILWAY_MYSQL_USER:?Set RAILWAY_MYSQL_USER in .env.railway.backup}"
: "${RAILWAY_MYSQL_PASSWORD:?Set RAILWAY_MYSQL_PASSWORD in .env.railway.backup}"
: "${RAILWAY_MYSQL_DATABASE:?Set RAILWAY_MYSQL_DATABASE in .env.railway.backup}"

if ! command -v mysqldump >/dev/null 2>&1; then
  echo "❌ mysqldump not found. Install mysql-client, e.g.: brew install mysql-client"
  exit 1
fi

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
LABEL="${BACKUP_LABEL:-railway}"
OUT_FILE="$BACKUP_DIR/${LABEL}-${STAMP}.sql.gz"

echo "📦 Backing up Railway MySQL..."
echo "   Host: $RAILWAY_MYSQL_HOST:$RAILWAY_MYSQL_PORT"
echo "   Database: $RAILWAY_MYSQL_DATABASE"
echo "   Output: $OUT_FILE"

MYSQL_PWD="$RAILWAY_MYSQL_PASSWORD" mysqldump \
  -h "$RAILWAY_MYSQL_HOST" \
  -P "$RAILWAY_MYSQL_PORT" \
  -u "$RAILWAY_MYSQL_USER" \
  --single-transaction \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  "$RAILWAY_MYSQL_DATABASE" | gzip > "$OUT_FILE"

BYTES="$(wc -c < "$OUT_FILE" | tr -d ' ')"
echo "✅ Backup complete ($(numfmt --to=iec-i --suffix=B "$BYTES" 2>/dev/null || echo "${BYTES} bytes"))"
echo "   Keep this file off-repo (backups/ is gitignored)."
