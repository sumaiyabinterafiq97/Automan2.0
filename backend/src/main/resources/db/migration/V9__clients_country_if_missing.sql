-- Ensure clients.country exists right after client_name.
-- Idempotent safety migration (some environments may have drifted/baselined history).

SET @ccountry := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'clients'
    AND COLUMN_NAME = 'country'
);

SET @add_country := IF(
  @ccountry = 0,
  'ALTER TABLE `clients` ADD COLUMN `country` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `client_name`',
  'SELECT 1'
);

PREPARE stmt_c FROM @add_country;
EXECUTE stmt_c;
DEALLOCATE PREPARE stmt_c;
