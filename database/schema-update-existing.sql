-- ===========================================
-- OPTION A: Schema update for EXISTING database
-- ===========================================
-- Use this when tables already exist and you only need to add missing columns.
-- Does NOT drop tables, NOT delete data, NOT re-run seed INSERTs.
--
-- Run on EC2:
--   mysql -h $RDS_ENDPOINT -u $RDS_USER -p$RDS_PASSWORD automan_car_purchase < database/schema-update-existing.sql
-- ===========================================

USE automan_car_purchase;

-- Add 'pol' to purchases if missing (idempotent: safe to run multiple times)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'pol');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE purchases ADD COLUMN pol VARCHAR(100) NULL AFTER stock_location',
    'SELECT 1 AS noop');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill POL for existing purchases so booking filters that now read purchases.pol
-- continue to find legacy rows. Prefer configured booking_mappings.pols, then keep
-- the old stock_location value as a compatibility fallback.
UPDATE purchases p
LEFT JOIN (
    SELECT
        UPPER(TRIM(stock_location)) AS normalized_stock_location,
        MIN(TRIM(SUBSTRING_INDEX(pols, ',', 1))) AS inferred_pol
    FROM booking_mappings
    WHERE stock_location IS NOT NULL
      AND TRIM(stock_location) <> ''
      AND pols IS NOT NULL
      AND TRIM(pols) <> ''
    GROUP BY UPPER(TRIM(stock_location))
) bm ON UPPER(TRIM(p.stock_location)) = bm.normalized_stock_location
SET p.pol = COALESCE(NULLIF(bm.inferred_pol, ''), NULLIF(TRIM(p.stock_location), ''))
WHERE (p.pol IS NULL OR TRIM(p.pol) = '')
  AND p.stock_location IS NOT NULL
  AND TRIM(p.stock_location) <> '';

-- Add other columns that may be missing on older RDS (idempotent; add more blocks as needed)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'total_fob_price');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE purchases ADD COLUMN total_fob_price DECIMAL(15,2) DEFAULT NULL',
    'SELECT 1 AS noop');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'booking_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE purchases ADD COLUMN booking_id BIGINT NULL',
    'SELECT 1 AS noop');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'car_pictures');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE purchases ADD COLUMN car_pictures TEXT DEFAULT NULL',
    'SELECT 1 AS noop');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'Schema update (Option A) completed.' AS result;
