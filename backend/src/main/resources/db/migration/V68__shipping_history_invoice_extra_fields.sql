-- Booking → shipping_history fields used by Create Invoice PDF (not shown on Shipping History UI).

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'cy_cut_date'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE shipping_history ADD COLUMN cy_cut_date DATE NULL AFTER shipment_date',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'eta'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE shipping_history ADD COLUMN eta DATE NULL AFTER cy_cut_date',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'final_destination'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE shipping_history ADD COLUMN final_destination VARCHAR(255) NULL AFTER pod',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'notify_party'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE shipping_history ADD COLUMN notify_party VARCHAR(512) NULL AFTER consignee',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
