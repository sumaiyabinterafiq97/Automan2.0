-- Remove persisted C&F/FOB totals from purchases (idempotent; matches V3 style).
SET @cnf := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'total_cnf_price'
);
SET @drop_cnf := IF(@cnf > 0, 'ALTER TABLE `purchases` DROP COLUMN `total_cnf_price`', 'SELECT 1');
PREPARE stmt_cnf FROM @drop_cnf;
EXECUTE stmt_cnf;
DEALLOCATE PREPARE stmt_cnf;

SET @fob := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'total_fob_price'
);
SET @drop_fob := IF(@fob > 0, 'ALTER TABLE `purchases` DROP COLUMN `total_fob_price`', 'SELECT 1');
PREPARE stmt_fob FROM @drop_fob;
EXECUTE stmt_fob;
DEALLOCATE PREPARE stmt_fob;
