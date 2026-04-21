-- Align live schema with database/01-init-multiplatform.sql (source of truth).
-- Idempotent: safe if some steps already applied.
-- Does NOT drop flyway_schema_history.

-- ---------------------------------------------------------------------------
-- 1) Tables present only on drifted RDS (not in init): drop backup clones first, then role_requests
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `booking_mappings_backup_20260325_210002`;

SET @rr_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'role_requests'
);
SET @ddl_rr := IF(@rr_exists > 0, 'DROP TABLE `role_requests`', 'SELECT 1');
PREPARE stmt_rr FROM @ddl_rr;
EXECUTE stmt_rr;
DEALLOCATE PREPARE stmt_rr;

-- ---------------------------------------------------------------------------
-- 2) clients: add country (init has it; older RDS may not)
-- ---------------------------------------------------------------------------
SET @ccountry := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clients' AND COLUMN_NAME = 'country'
);
SET @add_country := IF(
  @ccountry = 0,
  'ALTER TABLE `clients` ADD COLUMN `country` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `client_name`',
  'SELECT 1'
);
PREPARE stmt_c FROM @add_country;
EXECUTE stmt_c;
DEALLOCATE PREPARE stmt_c;

-- ---------------------------------------------------------------------------
-- 3) purchases: drop columns not in init; ensure total_fob_price exists; tighten decimals
-- ---------------------------------------------------------------------------
SET @pdest := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'destination'
);
SET @drop_dest := IF(@pdest > 0, 'ALTER TABLE `purchases` DROP COLUMN `destination`', 'SELECT 1');
PREPARE stmt_pd FROM @drop_dest;
EXECUTE stmt_pd;
DEALLOCATE PREPARE stmt_pd;

SET @pdisp := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'displacement'
);
SET @drop_disp := IF(@pdisp > 0, 'ALTER TABLE `purchases` DROP COLUMN `displacement`', 'SELECT 1');
PREPARE stmt_pdi FROM @drop_disp;
EXECUTE stmt_pdi;
DEALLOCATE PREPARE stmt_pdi;

SET @pstw := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'steering_wheel'
);
SET @drop_stw := IF(@pstw > 0, 'ALTER TABLE `purchases` DROP COLUMN `steering_wheel`', 'SELECT 1');
PREPARE stmt_ps FROM @drop_stw;
EXECUTE stmt_ps;
DEALLOCATE PREPARE stmt_ps;

SET @ppkg := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'package_price'
);
SET @drop_pkg := IF(@ppkg > 0, 'ALTER TABLE `purchases` DROP COLUMN `package_price`', 'SELECT 1');
PREPARE stmt_pp FROM @drop_pkg;
EXECUTE stmt_pp;
DEALLOCATE PREPARE stmt_pp;

SET @ptfob := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'total_fob_price'
);
SET @add_tfob := IF(
  @ptfob = 0,
  'ALTER TABLE `purchases` ADD COLUMN `total_fob_price` DECIMAL(15,2) DEFAULT NULL AFTER `total_cnf_price`',
  'SELECT 1'
);
PREPARE stmt_tfob FROM @add_tfob;
EXECUTE stmt_tfob;
DEALLOCATE PREPARE stmt_tfob;

ALTER TABLE `purchases` MODIFY COLUMN `profit` DECIMAL(15,2) DEFAULT 0;
ALTER TABLE `purchases` MODIFY COLUMN `total_cnf_price` DECIMAL(15,2) DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- 4) booking_mappings: drop extra columns not in init
-- ---------------------------------------------------------------------------
SET @bm_cn := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_mappings' AND COLUMN_NAME = 'client_name'
);
SET @bm_drop_cn := IF(@bm_cn > 0, 'ALTER TABLE `booking_mappings` DROP COLUMN `client_name`', 'SELECT 1');
PREPARE stmt_bmcn FROM @bm_drop_cn;
EXECUTE stmt_bmcn;
DEALLOCATE PREPARE stmt_bmcn;

SET @bm_n := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_mappings' AND COLUMN_NAME = 'notes'
);
SET @bm_drop_n := IF(@bm_n > 0, 'ALTER TABLE `booking_mappings` DROP COLUMN `notes`', 'SELECT 1');
PREPARE stmt_bmn FROM @bm_drop_n;
EXECUTE stmt_bmn;
DEALLOCATE PREPARE stmt_bmn;

SET @bm_pols := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_mappings' AND COLUMN_NAME = 'pols'
);
SET @bm_drop_pols := IF(@bm_pols > 0, 'ALTER TABLE `booking_mappings` DROP COLUMN `pols`', 'SELECT 1');
PREPARE stmt_bmp FROM @bm_drop_pols;
EXECUTE stmt_bmp;
DEALLOCATE PREPARE stmt_bmp;

SET @bm_sl := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_mappings' AND COLUMN_NAME = 'stock_location'
);
SET @bm_drop_sl := IF(@bm_sl > 0, 'ALTER TABLE `booking_mappings` DROP COLUMN `stock_location`', 'SELECT 1');
PREPARE stmt_bmsl FROM @bm_drop_sl;
EXECUTE stmt_bmsl;
DEALLOCATE PREPARE stmt_bmsl;

SET @bm_ca := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_mappings' AND COLUMN_NAME = 'created_at'
);
SET @bm_add_ca := IF(
  @bm_ca = 0,
  'ALTER TABLE `booking_mappings` ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP',
  'SELECT 1'
);
PREPARE stmt_bmca FROM @bm_add_ca;
EXECUTE stmt_bmca;
DEALLOCATE PREPARE stmt_bmca;

-- ---------------------------------------------------------------------------
-- 5) rixo_prices: drop columns not in init
-- ---------------------------------------------------------------------------
SET @rp_pol := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rixo_prices' AND COLUMN_NAME = 'pol'
);
SET @rp_drop_pol := IF(@rp_pol > 0, 'ALTER TABLE `rixo_prices` DROP COLUMN `pol`', 'SELECT 1');
PREPARE stmt_rpp FROM @rp_drop_pol;
EXECUTE stmt_rpp;
DEALLOCATE PREPARE stmt_rpp;

SET @rp_rp := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rixo_prices' AND COLUMN_NAME = 'rixo_price'
);
SET @rp_drop_rp := IF(@rp_rp > 0, 'ALTER TABLE `rixo_prices` DROP COLUMN `rixo_price`', 'SELECT 1');
PREPARE stmt_rpx FROM @rp_drop_rp;
EXECUTE stmt_rpx;
DEALLOCATE PREPARE stmt_rpx;

SET @rp_ah := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rixo_prices' AND COLUMN_NAME = 'auction_house'
);
SET @rp_drop_ah := IF(@rp_ah > 0, 'ALTER TABLE `rixo_prices` DROP COLUMN `auction_house`', 'SELECT 1');
PREPARE stmt_rpah FROM @rp_drop_ah;
EXECUTE stmt_rpah;
DEALLOCATE PREPARE stmt_rpah;

-- ---------------------------------------------------------------------------
-- 6) users / pending_signups: VARCHAR roles/status like init (not MySQL ENUM)
-- ---------------------------------------------------------------------------
ALTER TABLE `users` MODIFY COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'VIEWER';
ALTER TABLE `pending_signups` MODIFY COLUMN `role` VARCHAR(16) NOT NULL;
ALTER TABLE `pending_signups` MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING';
