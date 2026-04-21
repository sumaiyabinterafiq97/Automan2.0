-- Matches database/01-init-multiplatform.sql: idempotent idx_chassis for booking SEARCH CHASSIS (prefix).
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'purchases' AND index_name = 'idx_chassis'
);
SET @sql := IF(@exists > 0,
  'SELECT ''idx_chassis already present'' AS msg',
  'CREATE INDEX idx_chassis ON purchases (chassis)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
