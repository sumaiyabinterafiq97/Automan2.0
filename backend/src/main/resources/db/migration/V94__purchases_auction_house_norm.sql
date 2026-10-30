-- Indexed supplier lookup key: UPPER(TRIM(COALESCE(auction_house, ''))).
-- Nullable through expand phase; NOT NULL deferred until all writers maintain the column.

SET @db := DATABASE();
SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'purchases' AND column_name = 'auction_house_norm'
);
SET @sql := IF(@col_exists > 0,
  'SELECT ''auction_house_norm already present'' AS msg',
  'ALTER TABLE purchases ADD COLUMN auction_house_norm VARCHAR(100) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Abort before backfill if any row would exceed VARCHAR(100) after normalization (no silent truncation).
SET @too_long := (
  SELECT COUNT(*) FROM purchases
  WHERE CHAR_LENGTH(UPPER(TRIM(COALESCE(auction_house, '')))) > 100
);
SET @sql := IF(@too_long > 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''auction_house_norm: normalized value exceeds VARCHAR(100)''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE purchases
SET auction_house_norm = UPPER(TRIM(COALESCE(auction_house, '')))
WHERE auction_house_norm IS NULL;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'purchases' AND index_name = 'idx_purchases_auction_house_norm'
);
SET @sql := IF(@idx_exists > 0,
  'SELECT ''idx_purchases_auction_house_norm already present'' AS msg',
  'CREATE INDEX idx_purchases_auction_house_norm ON purchases (auction_house_norm)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
