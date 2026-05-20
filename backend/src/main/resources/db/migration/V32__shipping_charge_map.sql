-- Shipping charge map: per stock location, price per car by cars-per-container tier.

CREATE TABLE IF NOT EXISTS shipping_charge_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_location VARCHAR(100) NOT NULL,
    cars_per_container INT NOT NULL,
    shipping_price_per_car DECIMAL(18, 2) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shipping_charge_stock_cars (stock_location(64), cars_per_container),
    INDEX idx_shipping_charge_stock (stock_location(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO shipping_charge_map (stock_location, cars_per_container, shipping_price_per_car) VALUES
('KLC', 2, 17000.00),
('KLC', 3, 15000.00),
('KLC', 4, 14000.00),
('KLC', 5, 12000.00);
