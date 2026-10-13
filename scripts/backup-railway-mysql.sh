#!/usr/bin/env bash
# Export Railway MySQL to a local .sql.gz file (portable backup).
#
# This script only runs mysqldump. A dedicated MySQL account with read-only
# privileges is preferable to the administrative account in .env.railway.backup.
# This script does not create that account or change Railway settings.
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
MIN_BACKUP_BYTES=1024
TMP_FILE=""

cleanup() {
  local status=$?
  trap - EXIT
  if [ -n "${TMP_FILE}" ] && [ -f "${TMP_FILE}" ]; then
    rm -f -- "${TMP_FILE}"
  fi
  exit "$status"
}
trap cleanup EXIT

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

LABEL="${BACKUP_LABEL:-railway}"
case "$LABEL" in
  *[!A-Za-z0-9._-]* | "")
    echo "❌ BACKUP_LABEL may contain only letters, numbers, dot, underscore, and hyphen."
    exit 1
    ;;
esac

mkdir -p "$BACKUP_DIR"

OUT_FILE=""
attempt=0
while [ "$attempt" -lt 5 ]; do
  suffix="$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')"
  candidate="$BACKUP_DIR/${LABEL}-$(date +%Y%m%d-%H%M%S)-${suffix}.sql.gz"
  if [ ! -e "$candidate" ]; then
    OUT_FILE="$candidate"
    break
  fi
  attempt=$((attempt + 1))
done
if [ -z "$OUT_FILE" ]; then
  echo "❌ Could not choose an unused backup filename."
  exit 1
fi

echo "📦 Backing up Railway MySQL..."
echo "   Host: $RAILWAY_MYSQL_HOST:$RAILWAY_MYSQL_PORT"
echo "   Database: $RAILWAY_MYSQL_DATABASE"
echo "   Output: $OUT_FILE"

dump_args=(
  -h "$RAILWAY_MYSQL_HOST"
  -P "$RAILWAY_MYSQL_PORT"
  -u "$RAILWAY_MYSQL_USER"
  --single-transaction
  --routines
  --triggers
  --set-gtid-purged=OFF
)
if mysqldump --help 2>/dev/null | grep -q -- '--no-tablespaces'; then
  dump_args+=(--no-tablespaces)
fi

umask 077
TMP_FILE="$(mktemp "${BACKUP_DIR}/.dump.XXXXXX")"
chmod 600 "$TMP_FILE"

set +e
MYSQL_PWD="$RAILWAY_MYSQL_PASSWORD" mysqldump "${dump_args[@]}" "$RAILWAY_MYSQL_DATABASE" | gzip -c > "$TMP_FILE"
dump_status=${PIPESTATUS[0]}
gzip_status=${PIPESTATUS[1]}
set -e
if [ "$dump_status" -ne 0 ] || [ "$gzip_status" -ne 0 ]; then
  echo "❌ mysqldump or gzip failed. Partial output removed."
  exit 1
fi

if ! gzip -t "$TMP_FILE"; then
  echo "❌ gzip validation failed. Partial output removed."
  exit 1
fi

bytes="$(wc -c < "$TMP_FILE" | tr -d ' ')"
if [ "$bytes" -lt "$MIN_BACKUP_BYTES" ]; then
  echo "❌ Backup is ${bytes} bytes, below the ${MIN_BACKUP_BYTES}-byte minimum. Partial output removed."
  exit 1
fi

if [ -e "$OUT_FILE" ]; then
  echo "❌ Refusing to overwrite $OUT_FILE"
  exit 1
fi

mv -n "$TMP_FILE" "$OUT_FILE"
if [ -e "$TMP_FILE" ] || [ ! -f "$OUT_FILE" ]; then
  echo "❌ Failed to publish the backup without overwriting an existing file."
  exit 1
fi
TMP_FILE=""
chmod 600 "$OUT_FILE"

echo "✅ Backup complete ($(numfmt --to=iec-i --suffix=B "$bytes" 2>/dev/null || echo "${bytes} bytes"))"
echo "   Keep this file off-repo (backups/ is gitignored)."
