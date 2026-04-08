-- Client map: reference data per client name (logistics / billing fields).
-- IF NOT EXISTS: database/01-init-multiplatform.sql may create this table before Flyway runs (Docker).

CREATE TABLE IF NOT EXISTS client_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_name VARCHAR(255) NOT NULL,
    country VARCHAR(100) NULL,
    pod VARCHAR(255) NULL,
    address TEXT NULL,
    bank_info TEXT NULL,
    consignee TEXT NULL,
    debit_limit DECIMAL(18, 2) NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_client_map_client_name (client_name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
