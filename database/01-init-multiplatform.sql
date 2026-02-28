-- ===========================================
-- CLEANUP: Remove unused columns and tables (idempotent)
-- ===========================================
-- This section ensures old schema elements are removed before creating new ones
-- Safe to run multiple times - checks existence before dropping

USE automan_car_purchase;

-- Drop all _decimal columns from purchases table (if they exist)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'price_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN price_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'auction_fee_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN auction_fee_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'recycle_fee_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN recycle_fee_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'road_tax_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN road_tax_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'tax_total_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN tax_total_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'total_price_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN total_price_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'shipment_charges_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN shipment_charges_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'freight_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN freight_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'storage_charges_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN storage_charges_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'misc_charges_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN misc_charges_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'inspection_fee_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN inspection_fee_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'commission_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN commission_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'rixo_price_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN rixo_price_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'repair_charges_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN repair_charges_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'package_price_decimal');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN package_price_decimal', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop displacement and package_price columns (legacy, removed from schema)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'displacement');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN displacement', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'package_price');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN package_price', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop lot_number column and idx_lot_chassis index (if they exist)
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND INDEX_NAME = 'idx_lot_chassis');
SET @sql = IF(@index_exists > 0, 'ALTER TABLE purchases DROP INDEX idx_lot_chassis', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND COLUMN_NAME = 'lot_number');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE purchases DROP COLUMN lot_number', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop foreign key constraints to unused tables (if they exist)
SET @constraint_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND CONSTRAINT_NAME = 'fk_purchase_stock_location_id' LIMIT 1);
SET @sql = IF(@constraint_name IS NOT NULL, CONCAT('ALTER TABLE purchases DROP FOREIGN KEY ', @constraint_name), 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @constraint_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND CONSTRAINT_NAME = 'fk_purchase_repair_company_id' LIMIT 1);
SET @sql = IF(@constraint_name IS NOT NULL, CONCAT('ALTER TABLE purchases DROP FOREIGN KEY ', @constraint_name), 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @constraint_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND CONSTRAINT_NAME = 'fk_purchase_country_id' LIMIT 1);
SET @sql = IF(@constraint_name IS NOT NULL, CONCAT('ALTER TABLE purchases DROP FOREIGN KEY ', @constraint_name), 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @constraint_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND CONSTRAINT_NAME = 'fk_purchase_rixo_company_id' LIMIT 1);
SET @sql = IF(@constraint_name IS NOT NULL, CONCAT('ALTER TABLE purchases DROP FOREIGN KEY ', @constraint_name), 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @constraint_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND CONSTRAINT_NAME = 'fk_purchase_supplier_id' LIMIT 1);
SET @sql = IF(@constraint_name IS NOT NULL, CONCAT('ALTER TABLE purchases DROP FOREIGN KEY ', @constraint_name), 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop unused tables (if they exist)
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_calculations');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS booking_calculations', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookings');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS bookings', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vessels');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS vessels', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_locations');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS stock_locations', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'countries');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS countries', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'repair_companies');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS repair_companies', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rixo_companies');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS rixo_companies', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suppliers');
SET @sql = IF(@table_exists > 0, 'DROP TABLE IF EXISTS suppliers', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================
-- PURCHASES TABLE
-- ===========================================

-- Create the purchases table
-- Note: booking_id has no foreign key constraint to allow any number value
CREATE TABLE IF NOT EXISTS purchases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date VARCHAR(50),
    chassis VARCHAR(100) NOT NULL,
    car_model_year VARCHAR(10),
    brand VARCHAR(100),
    car_name VARCHAR(100),
    shipment_size VARCHAR(50),
    grade VARCHAR(100),
    `rank` VARCHAR(100),
    color VARCHAR(100),
    fuel VARCHAR(100),
    seat VARCHAR(100),
    door VARCHAR(100),
    distance VARCHAR(100),
    options TEXT,
    CC INT NULL,
    shift VARCHAR(50) NULL,
    WD VARCHAR(50) NULL,
    drive_type VARCHAR(50) NULL,
    auction_no VARCHAR(100),
    auction_house VARCHAR(100),
    stock_location VARCHAR(100),
    rixo_company VARCHAR(100),
    client_name VARCHAR(100),
    consignee TEXT DEFAULT NULL,
    client_id BIGINT,
    country VARCHAR(100),
    price VARCHAR(50),
    auction_fee VARCHAR(50),
    recycle_fee VARCHAR(50),
    road_tax VARCHAR(50),
    tax_total VARCHAR(50),
    total_price VARCHAR(50),
    payment_date VARCHAR(50),
    rixo_requested VARCHAR(10),
    rixo_confirmed VARCHAR(10),
    notes TEXT,
    shippment_date VARCHAR(50),
    `B/L_no` VARCHAR(100),
    vessel_no VARCHAR(100),
    vessel VARCHAR(255) DEFAULT NULL,
    destination VARCHAR(100),
    shipped BOOLEAN DEFAULT FALSE,
    shipment_charges VARCHAR(50),
    freight VARCHAR(50),
    storage_charges VARCHAR(50),
    misc_charges VARCHAR(50),
    inspection_fee VARCHAR(50),
    commission VARCHAR(50),
    rixo_price VARCHAR(50),
    venue_id VARCHAR(255),
    number_cut VARCHAR(255),
    shaken BOOLEAN DEFAULT FALSE,
    repair_company VARCHAR(100),
    repair_charges VARCHAR(50),
    profit DECIMAL(15,2) DEFAULT 0,
    is_package_mode BOOLEAN DEFAULT FALSE,
    total_cnf_price DECIMAL(15,2) DEFAULT NULL,
    total_fob_price DECIMAL(15,2) DEFAULT NULL,
    booking_id BIGINT NULL,
    car_pictures TEXT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_chassis (chassis)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===========================================
-- NOTE: Clients, Events, and Users tables
-- are created in separate migration files:
-- - database/10-clients-table.sql
-- - database/11-events-table.sql
-- - database/12-users-table.sql
-- ===========================================

-- Create indexes for purchases table (idempotent)
-- Check and create idx_date
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND INDEX_NAME = 'idx_date');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_date ON purchases(date)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and create idx_car_name
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND INDEX_NAME = 'idx_car_name');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_car_name ON purchases(car_name)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and create idx_auction_no
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND INDEX_NAME = 'idx_auction_no');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_auction_no ON purchases(auction_no)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and create idx_client_name
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND INDEX_NAME = 'idx_client_name');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_client_name ON purchases(client_name)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and create idx_purchase_client_id
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND INDEX_NAME = 'idx_purchase_client_id');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_purchase_client_id ON purchases(client_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add foreign key constraint for purchases.client_id (if not exists)
-- This references the clients table created in 10-clients-table.sql
SET @constraint_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND CONSTRAINT_NAME = 'fk_purchase_client_id' 
    LIMIT 1);
SET @sql = IF(@constraint_name IS NULL, 
    'ALTER TABLE purchases ADD CONSTRAINT fk_purchase_client_id FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL', 
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===========================================
-- PRE-POPULATED DATA FOR MULTI-PLATFORM IMAGE
-- ===========================================
-- Note: Sample data for clients, events, and users
-- is included in their respective migration files:
-- - database/10-clients-table.sql
-- - database/11-events-table.sql
-- - database/12-users-table.sql

-- Insert sample purchase data (3+ records)
INSERT INTO purchases (date, chassis, car_model_year, brand, car_name, auction_no, stock_location, rixo_company, client_name, client_id, country, price, auction_fee, rixo_price, shipment_charges, freight, inspection_fee, repair_charges, misc_charges, rixo_requested, rixo_confirmed, notes) VALUES
('24 Oct, 2025', 'JHMGD38408S123456', '2018', 'Honda', 'Civic', 'USS', 'Global Hakata', 'Rixo Japan', 'Tokyo Auto Import', 1, 'Japan', '15,500', '500', '45000', '5000', '400', '300', '200', '150', 'TRUE', 'TRUE', 'Sample purchase 1'),
('24 Oct, 2025', 'JT2BF28K123456789', '2015', 'Toyota', 'Prius', 'CAA', 'Global Hakata', 'Rixo Tokyo', 'Tokyo Auto Import', 1, 'Japan', '12,800', '400', '38000', '4000', '350', '250', '180', '120', 'TRUE', 'TRUE', 'Sample purchase 2'),
('24 Oct, 2025', 'WDB12345678901234', '2017', 'Mercedes', 'C-Class', 'TAA', 'Global Hakata', 'Rixo Osaka', 'Tokyo Auto Import', 1, 'Japan', '28,500', '800', '85000', '8000', '700', '500', '400', '300', 'TRUE', 'TRUE', 'Sample purchase 3'),
('24 Oct, 2025', '1HGBH41JXMN123456', '2019', 'Honda', 'Accord', 'USS', 'Global Hakata', 'Rixo Japan', 'Tokyo Auto Import', 1, 'Japan', '18,200', '600', '52000', '6000', '500', '400', '300', '200', 'TRUE', 'TRUE', 'Sample purchase 4');

-- ===========================================
-- BOOKING SYSTEM TABLES REMOVED
-- ===========================================
-- Note: bookings, booking_calculations, and vessels tables have been removed
-- booking_id column remains in purchases table but without foreign key constraints

-- Add drive_type column if it doesn't exist (for existing databases)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND COLUMN_NAME = 'drive_type');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE purchases ADD COLUMN drive_type VARCHAR(50) NULL', 
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add tax_total column if it doesn't exist (for existing databases)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'purchases' 
    AND COLUMN_NAME = 'tax_total');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE purchases ADD COLUMN tax_total VARCHAR(50) NULL', 
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create index for booking_id if needed (idempotent)
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases' AND INDEX_NAME = 'idx_purchase_booking_id');
SET @sql = IF(@index_exists = 0, 'CREATE INDEX idx_purchase_booking_id ON purchases(booking_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
