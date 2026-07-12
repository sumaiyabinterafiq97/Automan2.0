-- Carrier selected on booking page (from master_menu field `carrier`).

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'carrier'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE shipping_history ADD COLUMN carrier VARCHAR(255) NULL AFTER vessel',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
