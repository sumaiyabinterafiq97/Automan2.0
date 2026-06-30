-- Phase 4c: B/L number canonical on shipping_history (dual-write from purchases until V52).

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shipping_history'
    AND column_name = 'bl_no'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE shipping_history ADD COLUMN bl_no VARCHAR(100) NULL AFTER vessel',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE shipping_history sh
INNER JOIN purchases p ON TRIM(p.chassis) = TRIM(sh.chassis)
SET sh.bl_no = p.`B/L_no`
WHERE (sh.bl_no IS NULL OR TRIM(sh.bl_no) = '')
  AND p.`B/L_no` IS NOT NULL
  AND TRIM(p.`B/L_no`) <> '';
