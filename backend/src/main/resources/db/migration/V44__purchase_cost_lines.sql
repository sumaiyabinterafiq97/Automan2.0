-- Phase 2: Normalized purchase fee lines (dual-write with legacy VARCHAR columns).
-- Rollback: DROP TABLE IF EXISTS purchase_cost_lines;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'purchase_cost_lines'
);
SET @sql := IF(@exists = 0,
'CREATE TABLE purchase_cost_lines (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  purchase_id BIGINT NOT NULL,
  cost_code VARCHAR(64) NOT NULL,
  amount DECIMAL(15,2) NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pcl_purchase_cost (purchase_id, cost_code),
  KEY idx_pcl_purchase_id (purchase_id),
  CONSTRAINT fk_pcl_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
