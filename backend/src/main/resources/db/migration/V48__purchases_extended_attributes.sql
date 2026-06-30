-- Phase 4: Cold / misc purchase fields in JSON (dual-write with legacy columns).
-- Rollback: ALTER TABLE purchases DROP COLUMN extended_attributes;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'purchases'
    AND column_name = 'extended_attributes'
);
SET @sql := IF(@col_exists = 0,
'ALTER TABLE purchases ADD COLUMN extended_attributes JSON NULL AFTER car_pictures',
'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
