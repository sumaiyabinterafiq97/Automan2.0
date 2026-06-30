-- Phase 3: Per-purchase vehicle spec overrides (deltas vs car_brand_mapping baseline).
-- Rollback: DROP TABLE IF EXISTS purchase_vehicle_overrides;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'purchase_vehicle_overrides'
);
SET @sql := IF(@exists = 0,
'CREATE TABLE purchase_vehicle_overrides (
  purchase_id BIGINT NOT NULL PRIMARY KEY,
  overrides JSON NOT NULL,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_pvo_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
