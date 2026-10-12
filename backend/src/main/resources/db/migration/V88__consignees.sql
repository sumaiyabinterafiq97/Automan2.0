-- Consignee identities. legal_name is not unique: similar spellings are not the same entity.
-- Staff distinguish rows by id. No rows are inserted here.

CREATE TABLE consignees (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    legal_name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_consignees_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    KEY idx_consignees_legal_name (legal_name),
    KEY idx_consignees_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
