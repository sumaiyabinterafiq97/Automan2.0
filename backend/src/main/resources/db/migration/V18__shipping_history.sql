-- Shipping history: one row per chassis when saving from C&F/FOB calculation + car booking context.
CREATE TABLE shipping_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country VARCHAR(255),
    consignee VARCHAR(512),
    shipment_date DATE NULL,
    pol VARCHAR(255),
    pod VARCHAR(512),
    booking_id VARCHAR(255),
    vessel VARCHAR(255),
    price_type VARCHAR(16),
    chassis VARCHAR(255) NOT NULL,
    client_name VARCHAR(512),
    amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_shipping_history_booking_id (booking_id),
    INDEX idx_shipping_history_chassis (chassis),
    INDEX idx_shipping_history_shipment_date (shipment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
