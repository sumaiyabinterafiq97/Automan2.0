-- Clients Table Migration
-- Create table for client accounts management

USE automan_car_purchase;

CREATE TABLE IF NOT EXISTS clients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_number VARCHAR(50) UNIQUE NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(50),
    current_balance DECIMAL(15,2) DEFAULT 0,
    credit_limit DECIMAL(15,2),
    alert_threshold DECIMAL(15,2),
    currency VARCHAR(3) DEFAULT 'JPY',
    status ENUM('ACTIVE', 'SUSPENDED', 'CLOSED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create indexes for better performance (idempotent)
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clients' AND INDEX_NAME = 'idx_client_number');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_client_number ON clients(client_number)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clients' AND INDEX_NAME = 'idx_client_name');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_client_name ON clients(client_name)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clients' AND INDEX_NAME = 'idx_client_status');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_client_status ON clients(status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clients' AND INDEX_NAME = 'idx_client_balance');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_client_balance ON clients(current_balance)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add unique constraint for client_number (if not exists)
-- Note: The UNIQUE constraint is already defined in the CREATE TABLE statement above

-- ===========================================
-- SAMPLE DATA FOR MULTI-PLATFORM IMAGE
-- ===========================================

-- Insert sample client data
INSERT IGNORE INTO clients (client_number, client_name, address, phone, current_balance, credit_limit, alert_threshold, currency, status) VALUES
('C001', 'Tokyo Auto Import', 'Tokyo, Japan', '+81-3-1234-5678', 2500000.00, 50000000.00, 10000000.00, 'JPY', 'ACTIVE');

