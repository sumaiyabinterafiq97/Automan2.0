#!/usr/bin/env bash
# Restore a local .sql or .sql.gz dump into Railway MySQL.
#
# WARNING: This overwrites data in the target database. Use only on empty DB or when
# you intend to replace production data (take a fresh backup first).
#
# Usage:
#   ./scripts/restore-railway-mysql.sh backups/railway/railway-20260623-120000.sql.gz
#   CONFIRM=YES ./scripts/restore-railway-mysql.sh path/to/backup.sql

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${ENV_FILE:-$PROJECT_ROOT/.env.railway.backup}"

DUMP_FILE="${1:-}"
if [ -z "$DUMP_FILE" ] || [ ! -f "$DUMP_FILE" ]; then
  echo "Usage: $0 <backup.sql|backup.sql.gz>"
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ Missing $ENV_FILE (see .env.railway.backup.example)"
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

if ! command -v mysql >/dev/null 2>&1; then
  echo "❌ mysql client not found. Install mysql-client, e.g.: brew install mysql-client"
  exit 1
fi

if [ "${CONFIRM:-}" != "YES" ]; then
  echo "⚠️  This will import into Railway database: $RAILWAY_MYSQL_DATABASE"
  echo "   Host: $RAILWAY_MYSQL_HOST:$RAILWAY_MYSQL_PORT"
  echo "   File: $DUMP_FILE"
  echo ""
  echo "   Re-run with: CONFIRM=YES $0 \"$DUMP_FILE\""
  exit 1
fi

echo "🔄 Restoring $DUMP_FILE → $RAILWAY_MYSQL_DATABASE ..."

MYSQL_PWD="$RAILWAY_MYSQL_PASSWORD" mysql \
  -h "$RAILWAY_MYSQL_HOST" \
  -P "$RAILWAY_MYSQL_PORT" \
  -u "$RAILWAY_MYSQL_USER" \
  "$RAILWAY_MYSQL_DATABASE" <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
SET UNIQUE_CHECKS = 0;
SQL

if [[ "$DUMP_FILE" == *.gz ]]; then
  gunzip -c "$DUMP_FILE" | MYSQL_PWD="$RAILWAY_MYSQL_PASSWORD" mysql \
    -h "$RAILWAY_MYSQL_HOST" \
    -P "$RAILWAY_MYSQL_PORT" \
    -u "$RAILWAY_MYSQL_USER" \
    "$RAILWAY_MYSQL_DATABASE"
else
  MYSQL_PWD="$RAILWAY_MYSQL_PASSWORD" mysql \
    -h "$RAILWAY_MYSQL_HOST" \
    -P "$RAILWAY_MYSQL_PORT" \
    -u "$RAILWAY_MYSQL_USER" \
    "$RAILWAY_MYSQL_DATABASE" < "$DUMP_FILE"
fi

MYSQL_PWD="$RAILWAY_MYSQL_PASSWORD" mysql \
  -h "$RAILWAY_MYSQL_HOST" \
  -P "$RAILWAY_MYSQL_PORT" \
  -u "$RAILWAY_MYSQL_USER" \
  "$RAILWAY_MYSQL_DATABASE" -e "SET FOREIGN_KEY_CHECKS = 1; SET UNIQUE_CHECKS = 1;"

echo "✅ Restore finished. Verify flyway_schema_history and spot-check row counts."
