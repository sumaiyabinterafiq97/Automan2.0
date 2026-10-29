-- Indexed chassis lookup key: LOWER(TRIM(chassis)), matching legacy JPQL equality/prefix predicates.
-- Not UNIQUE: duplicate full chassis values and normalization collisions remain allowed.

SET @db := DATABASE();
SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'purchases' AND column_name = 'chassis_key'
);
SET @sql := IF(@col_exists > 0,
  'SELECT ''chassis_key already present'' AS msg',
  'ALTER TABLE purchases ADD COLUMN chassis_key VARCHAR(100) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE purchases SET chassis_key = LOWER(TRIM(chassis)) WHERE chassis_key IS NULL;

SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'purchases' AND column_name = 'chassis_key'
);
SET @sql := IF(@col_exists = 0,
  'SELECT 1',
  'ALTER TABLE purchases MODIFY chassis_key VARCHAR(100) NOT NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'purchases' AND index_name = 'idx_purchases_chassis_key'
);
SET @sql := IF(@idx_exists > 0,
  'SELECT ''idx_purchases_chassis_key already present'' AS msg',
  'CREATE INDEX idx_purchases_chassis_key ON purchases (chassis_key)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
