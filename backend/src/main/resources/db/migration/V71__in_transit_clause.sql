-- Consignee Map: multi-line Notify party + In-Transit Clause.
-- Shipping History: persist In-Transit Clause from booking.

-- booking_mappings.notify_party → TEXT (big-box content)
SET @col_type := (
  SELECT DATA_TYPE FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'booking_mappings'
    AND column_name = 'notify_party'
);
SET @sql := IF(@col_type IS NOT NULL AND @col_type <> 'longtext' AND @col_type <> 'text' AND @col_type <> 'mediumtext',
  'ALTER TABLE booking_mappings MODIFY COLUMN notify_party TEXT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'booking_mappings'
    AND column_name = 'in_transit_clause'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE booking_mappings ADD COLUMN in_transit_clause TEXT NULL AFTER notify_party',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- shipping_history.notify_party → TEXT
SET @col_type := (
  SELECT DATA_TYPE FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'notify_party'
);
SET @sql := IF(@col_type IS NOT NULL AND @col_type <> 'longtext' AND @col_type <> 'text' AND @col_type <> 'mediumtext',
  'ALTER TABLE shipping_history MODIFY COLUMN notify_party TEXT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'in_transit_clause'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE shipping_history ADD COLUMN in_transit_clause TEXT NULL AFTER notify_party',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
