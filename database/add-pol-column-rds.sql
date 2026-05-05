-- Add/backfill missing 'pol' column to purchases table on RDS (one-time migration).
-- Run this on EC2: mysql -h $RDS_ENDPOINT -u $RDS_USER -p$RDS_PASSWORD automan_car_purchase < database/add-pol-column-rds.sql

USE automan_car_purchase;

-- Add pol column if missing (idempotent: safe to run multiple times)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'pol');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE purchases ADD COLUMN pol VARCHAR(100) NULL AFTER stock_location',
    'SELECT 1 AS noop');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Existing purchases pre-date the new pol column. Booking filters now use
-- purchases.pol, so derive it from the canonical stock_location -> POL mapping.
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_mappings');
SET @sql = IF(@table_exists > 0,
    'UPDATE purchases p
     JOIN (
         SELECT
             stock_location,
             TRIM(SUBSTRING_INDEX(MIN(pols), CHAR(44), 1)) AS default_pol
         FROM booking_mappings
         WHERE stock_location IS NOT NULL
           AND LENGTH(TRIM(stock_location)) > 0
           AND pols IS NOT NULL
           AND LENGTH(TRIM(pols)) > 0
         GROUP BY stock_location
     ) bm ON LOWER(TRIM(p.stock_location)) = LOWER(TRIM(bm.stock_location))
     SET p.pol = bm.default_pol
     WHERE (p.pol IS NULL OR LENGTH(TRIM(p.pol)) = 0)
       AND LENGTH(bm.default_pol) > 0',
    'SELECT 1 AS noop');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
