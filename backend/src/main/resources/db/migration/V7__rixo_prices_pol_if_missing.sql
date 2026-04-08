-- Supplier Map UI stores POL; V3 dropped pol when aligning to an older init snapshot. Restore if missing.

SET @rp_pol := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rixo_prices' AND COLUMN_NAME = 'pol'
);
SET @add_pol := IF(
  @rp_pol = 0,
  'ALTER TABLE `rixo_prices` ADD COLUMN `pol` VARCHAR(255) NULL AFTER `venue_id`',
  'SELECT 1'
);
PREPARE stmt_rppol FROM @add_pol;
EXECUTE stmt_rppol;
DEALLOCATE PREPARE stmt_rppol;
