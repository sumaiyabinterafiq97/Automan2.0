-- Stock Location Map: one row per yard with optional multi-POL list and address.
-- Empty on migrate (no seed from rixo_mapping) so booking POL behavior is unchanged.

CREATE TABLE IF NOT EXISTS stock_location_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_location VARCHAR(100) NOT NULL,
    pol TEXT NULL,
    address TEXT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stock_location_map_stock (stock_location(64)),
    INDEX idx_stock_location_map_stock (stock_location(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
