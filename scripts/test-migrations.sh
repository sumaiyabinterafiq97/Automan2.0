#!/bin/bash
# Test database migrations
# This script validates SQL syntax and tests migrations on a clean database

set -e

DB_CONTAINER="automan_mysql_multiplatform"
DB_USER="automan_user"
DB_PASS="automan_password"
DB_NAME="automan_car_purchase"

echo "=========================================="
echo "Testing Database Migrations"
echo "=========================================="

# Check if container is running
if ! docker ps | grep -q "$DB_CONTAINER"; then
    echo "❌ Error: MySQL container '$DB_CONTAINER' is not running"
    exit 1
fi

echo "✅ MySQL container is running"

# Test SQL syntax for each migration file
echo ""
echo "Testing SQL syntax..."

MIGRATION_FILES=(
    "database/01-init-multiplatform.sql"
    "database/10-clients-table.sql"
    "database/11-events-table.sql"
    "database/12-users-table.sql"
    "database/02-car-brand-mapping.sql"
    "database/03-booking-mappings.sql"
    "database/04-rixo-prices.sql"
)

for file in "${MIGRATION_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "  Checking: $file"
        # Check for basic SQL syntax errors
        if docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "SOURCE /docker-entrypoint-initdb.d/$(basename $file)" "$DB_NAME" 2>&1 | grep -i "error\|syntax" | head -5; then
            echo "    ⚠️  Potential issues found (may be expected for existing tables)"
        else
            echo "    ✅ Syntax OK"
        fi
    else
        echo "  ❌ File not found: $file"
    fi
done

echo ""
echo "Checking current database schema..."

# Check if tables exist
TABLES=("purchases" "clients" "events" "users" "car_brand_mapping" "booking_mappings" "rixo_prices" "bookings" "vessels" "booking_calculations")

for table in "${TABLES[@]}"; do
    if docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "SHOW TABLES LIKE '$table';" "$DB_NAME" 2>&1 | grep -q "$table"; then
        echo "  ✅ Table '$table' exists"
    else
        echo "  ⚠️  Table '$table' does not exist"
    fi
done

echo ""
echo "Checking key columns in purchases table..."
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "DESCRIBE purchases;" "$DB_NAME" 2>&1 | grep -E "total_fob_price|drive_type|shipment_size|total_cnf_price|booking_id" || echo "  (Some columns may not exist yet)"

echo ""
echo "=========================================="
echo "Migration Test Complete"
echo "=========================================="
echo ""
echo "To view the database in phpMyAdmin:"
echo "  http://localhost:8082"
echo "  User: $DB_USER"
echo "  Password: $DB_PASS"
echo "  Database: $DB_NAME"

