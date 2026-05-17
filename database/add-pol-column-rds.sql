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

-- Populate POL for existing rows that only have stock_location.
UPDATE purchases p
JOIN booking_mappings bm
  ON UPPER(TRIM(bm.stock_location)) = UPPER(TRIM(p.stock_location))
 AND UPPER(TRIM(bm.country)) = 'STOCK_LOCATION_POL'
SET p.pol = TRIM(SUBSTRING_INDEX(bm.pols, ',', 1))
WHERE (p.pol IS NULL OR p.pol = '')
  AND p.stock_location IS NOT NULL
  AND p.stock_location != ''
  AND bm.pols IS NOT NULL
  AND bm.pols != '';
