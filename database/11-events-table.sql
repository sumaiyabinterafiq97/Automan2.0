-- Events Table Migration
-- Create table for client transaction events

USE automan_car_purchase;

CREATE TABLE IF NOT EXISTS events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    event_date DATE NOT NULL,
    event_type ENUM('PAYMENT_RECEIVED', 'SHIPMENT', 'ADJUSTMENT', 'OTHER') NOT NULL,
    event_description VARCHAR(500),
    quantity INT,
    bill_number VARCHAR(100),
    transaction_price DECIMAL(15,2),
    payment_received DECIMAL(15,2),
    running_balance DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create indexes for better performance (idempotent)
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events' AND INDEX_NAME = 'idx_event_client_id');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_event_client_id ON events(client_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events' AND INDEX_NAME = 'idx_event_date');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_event_date ON events(event_date)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events' AND INDEX_NAME = 'idx_event_type');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_event_type ON events(event_type)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events' AND INDEX_NAME = 'idx_event_balance');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_event_balance ON events(running_balance)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================
-- SAMPLE DATA FOR MULTI-PLATFORM IMAGE
-- ===========================================
-- Note: This assumes client with id=1 exists (created in 10-clients-table.sql)

-- Insert sample event data for the client
INSERT IGNORE INTO events (client_id, event_date, event_type, event_description, quantity, bill_number, transaction_price, payment_received, running_balance) VALUES
(1, '2025-10-20', 'PAYMENT_RECEIVED', 'Initial Payment', NULL, NULL, NULL, 1000000.00, 1500000.00),
(1, '2025-10-21', 'SHIPMENT', 'Honda Civic Export', 1, 'BL001', 15500.00, NULL, 1484500.00),
(1, '2025-10-22', 'PAYMENT_RECEIVED', 'Payment Received', NULL, NULL, NULL, 500000.00, 1984500.00),
(1, '2025-10-23', 'SHIPMENT', 'Toyota Prius Export', 1, 'BL002', 12800.00, NULL, 1971700.00);

