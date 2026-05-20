-- Audit trail for field-level edits via PUT /purchases/{id} (partial updates).
CREATE TABLE purchase_change_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    chassis VARCHAR(100) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    changed_by VARCHAR(256) NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_change_history_purchase
        FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE CASCADE,
    INDEX idx_pch_purchase_id (purchase_id),
    INDEX idx_pch_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
