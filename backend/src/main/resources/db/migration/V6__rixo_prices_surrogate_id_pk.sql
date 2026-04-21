-- Legacy Docker / RDS shapes used PRIMARY KEY (auction_name) with no `id` column.
-- The app (JPA + JDBC + LAST_INSERT_ID) requires BIGINT AUTO_INCREMENT `id` as primary key
-- and a unique constraint on auction_name.

SET @rp_id := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rixo_prices' AND COLUMN_NAME = 'id'
);
SET @migrate := IF(
  @rp_id = 0,
  'ALTER TABLE `rixo_prices` DROP PRIMARY KEY, ADD COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT FIRST, ADD PRIMARY KEY (`id`), ADD UNIQUE KEY `uk_rixo_prices_auction_name` (`auction_name`)',
  'SELECT 1'
);
PREPARE stmt_rpix FROM @migrate;
EXECUTE stmt_rpix;
DEALLOCATE PREPARE stmt_rpix;
