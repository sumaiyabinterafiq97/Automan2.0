-- Per-user UI preferences (Purchase List sort + columns, extensible).
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'views'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN views JSON NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
