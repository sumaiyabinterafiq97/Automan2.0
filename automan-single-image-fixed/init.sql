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
    profit DECIMAL(15,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lot_chassis (lot_number, chassis),
    UNIQUE KEY uk_chassis (chassis)
);

-- Insert sample data
INSERT INTO purchases (date, lot_number, chassis, car_model_year, brand, car_name, auction_no, stock_location, rixo_company, client_name, country, price, auction_fee, rixo_price, shipment_charges, freight, inspection_fee, repair_charges, misc_charges, rixo_requested, rixo_confirmed, notes) VALUES
('24 Apr, 2025', '100', 'KDH201-5012551', '2013', 'Toyota', 'Hiace', 'USS', 'Global Hakata', 'Rixo Japan', 'Tariq', 'South Africa', '25,500', '898', '89890', '9909', '789', '768', '899', '778', 'TRUE', 'TRUE', ''),
('24 Apr, 2025', '101', 'NZE141-9145340', '2010', 'Toyota', 'Corolla', 'CAA', 'Global Hakata', 'Rixo Tokyo', 'Arshad', 'Pakistan', '12,750', '500', '45000', '5000', '400', '300', '200', '150', 'TRUE', 'TRUE', ''),
('24 Apr, 2025', '102', 'ZRR75-0084692', '2011', 'Toyota', 'Noah', 'TAA', 'Global Hakata', 'Rixo Osaka', 'Jawad', 'Pakistan', '15,800', '600', '55000', '6000', '500', '400', '300', '200', 'TRUE', 'TRUE', '');

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

-- Insert sample client data (Crown Eagle account)
INSERT INTO clients (client_number, client_name, address, phone, current_balance, credit_limit, alert_threshold, currency, status) VALUES
('125', 'CROWN EAGLE', 'Tokyo, Japan', '+81-3-1234-5678', -36347577.00, 50000000.00, 10000000.00, 'JPY', 'ACTIVE'),
('126', 'TOKYO AUTO', 'Osaka, Japan', '+81-6-9876-5432', 2500000.00, 30000000.00, 5000000.00, 'JPY', 'ACTIVE'),
('127', 'NAGOYA MOTORS', 'Nagoya, Japan', '+81-52-1111-2222', -5000000.00, 20000000.00, 3000000.00, 'JPY', 'ACTIVE');

-- Insert sample event data for Crown Eagle (based on the CSV data)
INSERT INTO events (client_id, event_date, event_type, event_description, quantity, bill_number, transaction_price, payment_received, running_balance) VALUES
(1, '2025-03-20', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 5057518.00, -41405095.00),
(1, '2025-03-25', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 2962314.00, -44367409.00),
(1, '2025-03-24', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 927520.00, -45294929.00),
(1, '2025-03-21', 'SHIPMENT', 'MSC BASIL-HI513A', 10, '39461', 7120000.00, NULL, -38174929.00),
(1, '2025-03-28', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 567454.00, -38742383.00),
(1, '2025-04-01', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 748150.00, -39490533.00),
(1, '2025-04-02', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 7586241.00, -47076774.00),
(1, '2025-04-03', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 479960.00, -47556734.00),
(1, '2025-04-03', 'SHIPMENT', 'CAPTAIN THANASIS I-HG514A', 16, '99471', 9900000.00, NULL, -37656734.00),
(1, '2025-04-04', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 291660.00, -37948394.00),
(1, '2025-04-08', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 516565.00, -38464959.00),
(1, '2025-04-08', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 871200.00, -39336159.00),
(1, '2025-04-09', 'SHIPMENT', 'MSC MANHATTAN V-HI515A', 15, '9472', 7755000.00, NULL, -31581159.00),
(1, '2025-04-09', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 5867530.00, -37448689.00),
(1, '2025-04-12', 'SHIPMENT', 'CAPTAIN THANASIS I-HG514A', 4, '59489', 1390000.00, NULL, -36058689.00),
(1, '2025-04-16', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 254773.00, -36313462.00),
(1, '2025-04-16', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 7207865.00, -43521327.00),
(1, '2025-04-18', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 583430.00, -44104757.00),
(1, '2025-04-18', 'SHIPMENT', 'MSC GENERAL IV-HI516A', 6, '29504', 3445000.00, NULL, -40659757.00),
(1, '2025-04-20', 'SHIPMENT', 'MSC AUDREY-GS512S', 20, '39506', 10070000.00, NULL, -30589757.00),
(1, '2025-04-25', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 373761.00, -30963518.00),
(1, '2025-04-30', 'SHIPMENT', 'MSC PRECISION V HI517A', 7, '29523', 4100000.00, NULL, -26863518.00),
(1, '2025-05-02', 'PAYMENT_RECEIVED', 'TT RECIEVED(CASH 5-7)', NULL, NULL, NULL, 528693.00, -27392211.00),
(1, '2025-05-07', 'SHIPMENT', 'MSC FORTUNE F-XA518A', 11, '9538', 5415000.00, NULL, -21977211.00),
(1, '2025-05-08', 'SHIPMENT', 'CAPTHAIN THANASIS I-HG518A', 8, '9539', 3700000.00, NULL, -18277211.00),
(1, '2025-05-07', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 7258812.00, -25536023.00),
(1, '2025-05-09', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 335018.00, -25871041.00),
(1, '2025-05-14', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 294380.00, -26165421.00),
(1, '2025-05-14', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 5092774.00, -31258195.00),
(1, '2025-05-15', 'SHIPMENT', 'MAERSK VIRGINIA 520S', 10, '9540', 6030000.00, NULL, -25228195.00),
(1, '2025-05-16', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 212942.00, -25441137.00),
(1, '2025-05-20', 'SHIPMENT', 'VIRGO V.520W', 16, '39548', 7465000.00, NULL, -17976137.00),
(1, '2025-05-22', 'SHIPMENT', 'NAVIOS TEMPO V.521S', 4, '39549', 2000000.00, NULL, -15976137.00),
(1, '2025-05-26', 'PAYMENT_RECEIVED', 'TT RECIEVED', NULL, NULL, NULL, 598710.00, -16574847.00);

-- Create indexes for better performance
CREATE INDEX idx_date ON purchases(date);
CREATE INDEX idx_car_name ON purchases(car_name);
CREATE INDEX idx_auction_no ON purchases(auction_no);
CREATE INDEX idx_client_name ON purchases(client_name);

-- Create indexes for clients table
CREATE INDEX idx_client_number ON clients(client_number);
CREATE INDEX idx_client_name ON clients(client_name);
CREATE INDEX idx_client_status ON clients(status);
CREATE INDEX idx_client_balance ON clients(current_balance);

-- Create indexes for events table
CREATE INDEX idx_event_client_id ON events(client_id);
CREATE INDEX idx_event_date ON events(event_date);
CREATE INDEX idx_event_type ON events(event_type);
CREATE INDEX idx_event_balance ON events(running_balance);

-- Add foreign key constraint for purchases table
ALTER TABLE purchases ADD CONSTRAINT fk_purchase_client_id FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;

-- Create index for purchases client_id
CREATE INDEX idx_purchase_client_id ON purchases(client_id);

-- Update existing purchases to have client_id based on client_name
UPDATE purchases SET client_id = 1 WHERE client_name = 'Tariq';
UPDATE purchases SET client_id = 2 WHERE client_name = 'Arshad';
UPDATE purchases SET client_id = 3 WHERE client_name = 'Jawad';

-- Create users table for authentication
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'VIEWER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for users table
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- No default admin user - clients will create their own during first setup
-- This ensures clients have full control over their admin account

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
