-- rixo_prices no longer uses type_of_vehicle (matches database/01-init-multiplatform.sql).
-- Drop if present (e.g. from Flyway V1 or older schemas).

SET @col := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'rixo_prices'
    AND COLUMN_NAME = 'type_of_vehicle'
);
SET @ddl := IF(
  @col > 0,
  'ALTER TABLE `rixo_prices` DROP COLUMN `type_of_vehicle`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
