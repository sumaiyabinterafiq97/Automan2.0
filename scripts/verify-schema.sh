#!/bin/bash
# Verify database schema matches expected structure

set -e

DB_CONTAINER="automan_mysql_multiplatform"
DB_USER="automan_user"
DB_PASS="automan_password"
DB_NAME="automan_car_purchase"

echo "=========================================="
echo "Database Schema Verification"
echo "=========================================="
echo ""

# Check purchases table columns
echo "Purchases Table - Key Columns:"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
SELECT 
    COLUMN_NAME, 
    DATA_TYPE, 
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = '$DB_NAME' 
AND TABLE_NAME = 'purchases' 
AND COLUMN_NAME IN (
    'id', 'chassis', 'client_id', 
    'shipment_size', 'drive_type', 'total_cnf_price', 
    'total_fob_price', 'booking_id', 'car_pictures',
    'is_package_mode', 'venue_id',
    'tax_total', 'vessel', 'consignee'
)
ORDER BY COLUMN_NAME;
" 2>&1 | grep -v "Warning"

echo ""
echo "All Tables:"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SHOW TABLES;" 2>&1 | grep -v "Warning" | tail -n +2

echo ""
echo "Clients Table Structure:"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "DESCRIBE clients;" 2>&1 | grep -v "Warning"

echo ""
echo "Events Table Structure:"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "DESCRIBE events;" 2>&1 | grep -v "Warning"

echo ""
echo "Users Table Structure:"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "DESCRIBE users;" 2>&1 | grep -v "Warning"

echo ""
echo "Rixo Prices Table - Sample Data Count:"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT COUNT(*) as total_rows FROM rixo_prices;" 2>&1 | grep -v "Warning" | tail -1

echo ""
echo "=========================================="
echo "Verification Complete"
echo "=========================================="

