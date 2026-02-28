-- Pending signups table (admin approval email verification)
USE automan_car_purchase;

CREATE TABLE IF NOT EXISTS pending_signups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL,
    name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(16) NOT NULL,
    verification_token VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    INDEX idx_pending_signups_email (email),
    INDEX idx_pending_signups_token (verification_token),
    INDEX idx_pending_signups_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
