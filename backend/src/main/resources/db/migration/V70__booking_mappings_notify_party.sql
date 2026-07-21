-- Consignee Map: optional Notify party (source for Booking Notify party combobox).

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'booking_mappings'
    AND column_name = 'notify_party'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE booking_mappings ADD COLUMN notify_party VARCHAR(512) NULL AFTER pod',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
