-- Persist booking/shipping extras on invoice_history so PDF regenerate stays accurate.

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'invoice_history'
    AND column_name = 'booking_no'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE invoice_history ADD COLUMN booking_no VARCHAR(128) NULL AFTER vessel',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'invoice_history'
    AND column_name = 'carrier'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE invoice_history ADD COLUMN carrier VARCHAR(255) NULL AFTER booking_no',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'invoice_history'
    AND column_name = 'consignee'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE invoice_history ADD COLUMN consignee VARCHAR(512) NULL AFTER client_name',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'invoice_history'
    AND column_name = 'notify_party'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE invoice_history ADD COLUMN notify_party VARCHAR(512) NULL AFTER consignee',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'invoice_history'
    AND column_name = 'cy_cut_date'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE invoice_history ADD COLUMN cy_cut_date DATE NULL AFTER shipping_date',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'invoice_history'
    AND column_name = 'eta'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE invoice_history ADD COLUMN eta DATE NULL AFTER cy_cut_date',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'invoice_history'
    AND column_name = 'final_destination'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE invoice_history ADD COLUMN final_destination VARCHAR(255) NULL AFTER pod',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
