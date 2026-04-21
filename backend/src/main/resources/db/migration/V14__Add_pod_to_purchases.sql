-- Port of discharge (POD) on purchases; distinct from pol (port of loading).
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'purchases' AND column_name = 'pod'
);
SET @sql := IF(@exists > 0,
  'SELECT ''purchases.pod already present'' AS msg',
  'ALTER TABLE purchases ADD COLUMN pod VARCHAR(255) NULL AFTER pol'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
