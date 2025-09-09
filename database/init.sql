-- Create the purchases table
CREATE TABLE IF NOT EXISTS purchases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date VARCHAR(50),
    lot_number VARCHAR(50) NOT NULL,
    chasis VARCHAR(100) NOT NULL,
    car_model_year VARCHAR(10),
    brand VARCHAR(100),
    car_name VARCHAR(100),
    grade VARCHAR(100),
    `rank` VARCHAR(100),
    color VARCHAR(100),
    displacement VARCHAR(100),
    fuel VARCHAR(100),
    seat VARCHAR(100),
    door VARCHAR(100),
    distance VARCHAR(100),
    options TEXT,
    auction_no VARCHAR(100),
    auction_name VARCHAR(100),
    stock_location VARCHAR(100),
    rixo_company VARCHAR(100),
    client_name VARCHAR(100),
    country VARCHAR(100),
    price VARCHAR(50),
    auction_fee VARCHAR(50),
    recycle_fee VARCHAR(50),
    road_tax VARCHAR(50),
    total_price VARCHAR(50),
    payment_date VARCHAR(50),
    rixo_requested VARCHAR(10),
    rixo_confirmed VARCHAR(10),
    notes TEXT,
    shippment_date VARCHAR(50),
    `B/L_no` VARCHAR(100),
    vessel_no VARCHAR(100),
    destination VARCHAR(100),
    shipment_charges VARCHAR(50),
    freight VARCHAR(50),
    storage_charges VARCHAR(50),
    misc_charges VARCHAR(50),
    inspection_fee VARCHAR(50),
    commission VARCHAR(50),
    rixo_price VARCHAR(50),
    repair_company VARCHAR(100),
    repair_charges VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lot_chasis (lot_number, chasis)
);

-- Insert sample data
INSERT INTO purchases (date, lot_number, chasis, car_model_year, brand, car_name, auction_name, stock_location, rixo_company, client_name, country, price, rixo_requested, rixo_confirmed, notes) VALUES
('24 Apr, 2025', '100', 'KDH201-5012551', '2013', 'Toyota', 'Hiace', 'USS', 'Global Hakata', 'Rixo Japan', 'Tariq', 'South Africa', '$25,500', 'TRUE', 'TRUE', ''),
('24 Apr, 2025', '101', 'NZE141-9145340', '2010', 'Toyota', 'Corolla', 'CAA', 'Global Hakata', 'Rixo Tokyo', 'Arshad', 'Pakistan', '$12,750', 'TRUE', 'TRUE', ''),
('24 Apr, 2025', '102', 'ZRR75-0084692', '2011', 'Toyota', 'Noah', 'TAA', 'Global Hakata', 'Rixo Osaka', 'Jawad', 'Pakistan', '$15,800', 'TRUE', 'TRUE', '');

-- Create indexes for better performance
CREATE INDEX idx_date ON purchases(date);
CREATE INDEX idx_car_name ON purchases(car_name);
CREATE INDEX idx_auction_name ON purchases(auction_name);
CREATE INDEX idx_client_name ON purchases(client_name);
