-- Remove contact/location columns from clients (moved to client_map / not used on client accounts).

SET @ccountry := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'clients'
    AND COLUMN_NAME = 'country'
);

SET @drop_country := IF(
  @ccountry > 0,
  'ALTER TABLE `clients` DROP COLUMN `country`',
  'SELECT 1'
);

PREPARE stmt_dc FROM @drop_country;
EXECUTE stmt_dc;
DEALLOCATE PREPARE stmt_dc;

SET @caddress := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'clients'
    AND COLUMN_NAME = 'address'
);

SET @drop_address := IF(
  @caddress > 0,
  'ALTER TABLE `clients` DROP COLUMN `address`',
  'SELECT 1'
);

PREPARE stmt_da FROM @drop_address;
EXECUTE stmt_da;
DEALLOCATE PREPARE stmt_da;

SET @cphone := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'clients'
    AND COLUMN_NAME = 'phone'
);

SET @drop_phone := IF(
  @cphone > 0,
  'ALTER TABLE `clients` DROP COLUMN `phone`',
  'SELECT 1'
);

PREPARE stmt_dp FROM @drop_phone;
EXECUTE stmt_dp;
DEALLOCATE PREPARE stmt_dp;
