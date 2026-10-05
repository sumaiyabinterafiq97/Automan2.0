-- Rixo Email Map: one row is one address. The same company can have many rows.
-- Empty on migrate so Email PDF stays blank until a row is added.

CREATE TABLE IF NOT EXISTS rixo_email_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rixo_company VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rixo_email_map_company_email (rixo_company, email),
    INDEX idx_rixo_email_map_company (rixo_company)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
