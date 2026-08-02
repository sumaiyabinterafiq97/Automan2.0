-- Consignee Map: Final Destination (multi-chip free text; same family as notify / in-transit).
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'booking_mappings'
    AND column_name = 'final_destination'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE booking_mappings ADD COLUMN final_destination TEXT NULL AFTER pod',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
