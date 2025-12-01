-- Create the purchases table
CREATE TABLE IF NOT EXISTS purchases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date VARCHAR(50),
    lot_number VARCHAR(50) NOT NULL,
    chassis VARCHAR(100) NOT NULL,
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
    stock_location VARCHAR(100),
    rixo_company VARCHAR(100),
    client_name VARCHAR(100),
    client_id BIGINT,
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
    shaken BOOLEAN DEFAULT FALSE,
    number_cut VARCHAR(255),
    profit DECIMAL(15,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lot_chassis (lot_number, chassis),
    UNIQUE KEY uk_chassis (chassis)
);

-- Create the clients table
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
);

-- Create the events table
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
);

-- Create users table for authentication
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'VIEWER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_date ON purchases(date);
CREATE INDEX idx_car_name ON purchases(car_name);
CREATE INDEX idx_auction_no ON purchases(auction_no);
CREATE INDEX idx_client_name ON purchases(client_name);
CREATE INDEX idx_client_number ON clients(client_number);
CREATE INDEX idx_client_name ON clients(client_name);
CREATE INDEX idx_client_status ON clients(status);
CREATE INDEX idx_client_balance ON clients(current_balance);
CREATE INDEX idx_event_client_id ON events(client_id);
CREATE INDEX idx_event_date ON events(event_date);
CREATE INDEX idx_event_type ON events(event_type);
CREATE INDEX idx_event_balance ON events(running_balance);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Add foreign key constraint for purchases table
ALTER TABLE purchases ADD CONSTRAINT fk_purchase_client_id FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;

-- Create index for purchases client_id
CREATE INDEX idx_purchase_client_id ON purchases(client_id);

-- ===========================================
-- PRE-POPULATED DATA FOR MULTI-PLATFORM IMAGE
-- ===========================================

-- Insert pre-configured admin user
-- Password: password (BCrypt hashed)
INSERT INTO users (email, name, password_hash, role, created_at) VALUES
('admin@automan.com', 'System Administrator', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'ADMIN', NOW());

-- Insert sample client data
INSERT INTO clients (client_number, client_name, address, phone, current_balance, credit_limit, alert_threshold, currency, status) VALUES
('C001', 'Tokyo Auto Import', 'Tokyo, Japan', '+81-3-1234-5678', 2500000.00, 50000000.00, 10000000.00, 'JPY', 'ACTIVE');

-- Insert sample purchase data (3+ records)
INSERT INTO purchases (date, lot_number, chassis, car_model_year, brand, car_name, auction_no, stock_location, rixo_company, client_name, client_id, country, price, auction_fee, rixo_price, shipment_charges, freight, inspection_fee, repair_charges, misc_charges, rixo_requested, rixo_confirmed, notes) VALUES
('24 Oct, 2025', 'LOT001', 'JHMGD38408S123456', '2018', 'Honda', 'Civic', 'USS', 'Global Hakata', 'Rixo Japan', 'Tokyo Auto Import', 1, 'Japan', '15,500', '500', '45000', '5000', '400', '300', '200', '150', 'TRUE', 'TRUE', 'Sample purchase 1'),
('24 Oct, 2025', 'LOT002', 'JT2BF28K123456789', '2015', 'Toyota', 'Prius', 'CAA', 'Global Hakata', 'Rixo Tokyo', 'Tokyo Auto Import', 1, 'Japan', '12,800', '400', '38000', '4000', '350', '250', '180', '120', 'TRUE', 'TRUE', 'Sample purchase 2'),
('24 Oct, 2025', 'LOT003', 'WDB12345678901234', '2017', 'Mercedes', 'C-Class', 'TAA', 'Global Hakata', 'Rixo Osaka', 'Tokyo Auto Import', 1, 'Japan', '28,500', '800', '85000', '8000', '700', '500', '400', '300', 'TRUE', 'TRUE', 'Sample purchase 3'),
('24 Oct, 2025', 'LOT004', '1HGBH41JXMN123456', '2019', 'Honda', 'Accord', 'USS', 'Global Hakata', 'Rixo Japan', 'Tokyo Auto Import', 1, 'Japan', '18,200', '600', '52000', '6000', '500', '400', '300', '200', 'TRUE', 'TRUE', 'Sample purchase 4');

-- Insert sample event data for the client
INSERT INTO events (client_id, event_date, event_type, event_description, quantity, bill_number, transaction_price, payment_received, running_balance) VALUES
(1, '2025-10-20', 'PAYMENT_RECEIVED', 'Initial Payment', NULL, NULL, NULL, 1000000.00, 1500000.00),
(1, '2025-10-21', 'SHIPMENT', 'Honda Civic Export', 1, 'BL001', 15500.00, NULL, 1484500.00),
(1, '2025-10-22', 'PAYMENT_RECEIVED', 'Payment Received', NULL, NULL, NULL, 500000.00, 1984500.00),
(1, '2025-10-23', 'SHIPMENT', 'Toyota Prius Export', 1, 'BL002', 12800.00, NULL, 1971700.00);

-- ===========================================
-- BOOKING SYSTEM TABLES
-- ===========================================

-- Create bookings table for car booking management
CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_number VARCHAR(50) UNIQUE NOT NULL,
    vessel_no VARCHAR(100),
    vessel_name VARCHAR(200),
    consignee_country VARCHAR(100),
    pol_port VARCHAR(100),
    booking_date DATE,
    status ENUM('DRAFT', 'CONFIRMED', 'SHIPPED') DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create vessels lookup table
CREATE TABLE IF NOT EXISTS vessels (
    vessel_no VARCHAR(100) PRIMARY KEY,
    vessel_name VARCHAR(200) NOT NULL,
    company VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create booking_calculations table for cost calculations
CREATE TABLE IF NOT EXISTS booking_calculations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    calculation_type ENUM('FREIGHT', 'CAF', 'FOB', 'PAKISTAN') NOT NULL,
    container_price DECIMAL(15,2) DEFAULT 0,
    shipping_charge DECIMAL(15,2) DEFAULT 0,
    wc_charge DECIMAL(15,2) DEFAULT 0,
    inspection_fee DECIMAL(15,2) DEFAULT 0,
    fob_price DECIMAL(15,2) DEFAULT 0,
    freight_price DECIMAL(15,2) DEFAULT 0,
    insurance DECIMAL(15,2) DEFAULT 0,
    total_price DECIMAL(15,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

-- Add booking_id column to purchases table
ALTER TABLE purchases ADD COLUMN booking_id BIGINT;
ALTER TABLE purchases ADD CONSTRAINT fk_purchase_booking_id FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE SET NULL;

-- Create indexes for performance
CREATE INDEX idx_booking_number ON bookings(booking_number);
CREATE INDEX idx_booking_status ON bookings(status);
CREATE INDEX idx_booking_vessel_no ON bookings(vessel_no);
CREATE INDEX idx_purchase_booking_id ON purchases(booking_id);
CREATE INDEX idx_booking_calculation_booking_id ON booking_calculations(booking_id);
CREATE INDEX idx_booking_calculation_type ON booking_calculations(calculation_type);

-- Insert sample vessel data
INSERT INTO vessels (vessel_no, vessel_name, company) VALUES
('MSC123', 'MSC BASIL', 'MSC'),
('MSC456', 'MSC MANHATTAN V', 'MSC'),
('CAP789', 'CAPTAIN THANASIS I', 'CAPTAIN'),
('MAE012', 'MAERSK VIRGINIA', 'MAERSK'),
('VIR345', 'VIRGO V', 'VIRGO'),
('NAV678', 'NAVIOS TEMPO V', 'NAVIOS');
