-- Rixo transport PDF downloads: Extra message + selected chassis per save (Rixo Request Generator).
CREATE TABLE rixo_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buying_date DATE NULL,
    rixo_company VARCHAR(255),
    message TEXT,
    chassis TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rixo_history_buying_date (buying_date),
    INDEX idx_rixo_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
