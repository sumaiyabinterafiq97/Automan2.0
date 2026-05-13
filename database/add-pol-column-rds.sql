-- Add missing 'pol' column to purchases table on RDS (one-time migration).
-- Run this on EC2: mysql -h $RDS_ENDPOINT -u $RDS_USER -p$RDS_PASSWORD automan_car_purchase < database/add-pol-column-rds.sql

USE automan_car_purchase;

-- Add pol column if missing (safe to run multiple times)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'pol');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE purchases ADD COLUMN pol VARCHAR(100) NULL AFTER stock_location',
    'SELECT 1 AS noop');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill POL for stock locations with a single canonical POL mapping.
UPDATE purchases p
JOIN booking_mappings bm
    ON bm.country = 'STOCK_LOCATION_POL'
    AND bm.stock_location = p.stock_location
SET p.pol = bm.pols
WHERE (p.pol IS NULL OR p.pol = '')
  AND bm.pols IS NOT NULL
  AND bm.pols <> ''
  AND bm.pols NOT LIKE '%,%';
