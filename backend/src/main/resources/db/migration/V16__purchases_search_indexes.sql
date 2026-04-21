-- Filtered search on brand and supplier (auction_house). Idempotent: Docker init
-- (01-init-multiplatform.sql) may already define these on the purchases table.
SET @exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'purchases' AND index_name = 'idx_purchases_brand'
);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_purchases_brand ON purchases (brand(100))', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'purchases' AND index_name = 'idx_purchases_auction_house'
);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_purchases_auction_house ON purchases (auction_house(100))', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
