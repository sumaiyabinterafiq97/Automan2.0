-- ===========================================
-- AUTOMAN CAR PURCHASE DATABASE INITIALIZATION
-- ===========================================
-- This script creates all tables and seeds essential data.
-- Run this on a fresh database or when setting up a new environment.
--
-- IMPORTANT: This script contains REQUIRED seed data that the application
-- depends on. Without this data, the following features will NOT work:
--   - User login (users table)
--   - Form dropdowns (master_menu table)
--   - Chassis auto-fill (car_brand_mapping table)
--   - Consignee/POD/POL auto-fill (booking_mappings table)
--   - Rixo price lookup (rixo_prices table)
-- ===========================================

USE automan_car_purchase;

-- ===========================================
-- TABLE CREATION: CORE TABLES
-- ===========================================

-- Users table: Authentication and authorization
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'VIEWER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pending signups table: Admin approval email verification
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

-- Role requests table: User role upgrade requests
CREATE TABLE IF NOT EXISTS role_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    requested_role VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    INDEX idx_role_requests_user_id (user_id),
    INDEX idx_role_requests_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Clients table: Client accounts management
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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_client_number (client_number),
    INDEX idx_client_name (client_name),
    INDEX idx_client_status (status),
    INDEX idx_client_balance (current_balance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Events table: Client transaction events
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
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    INDEX idx_event_client_id (client_id),
    INDEX idx_event_date (event_date),
    INDEX idx_event_type (event_type),
    INDEX idx_event_balance (running_balance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Purchases table: Main car purchase records
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
    pol VARCHAR(100),
    rixo_company VARCHAR(100),
    client_name VARCHAR(100),
    consignee TEXT DEFAULT NULL,
    client_id BIGINT,
    country VARCHAR(100),
    price VARCHAR(50),
    auction_fee VARCHAR(50),
    auction_penalty_fee VARCHAR(50),
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
    INDEX idx_chassis (chassis),
    INDEX idx_date (date),
    INDEX idx_car_name (car_name),
    INDEX idx_auction_no (auction_no),
    INDEX idx_client_name (client_name),
    INDEX idx_purchase_client_id (client_id),
    INDEX idx_purchase_booking_id (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Master menu table: Configurable dropdown values for forms
CREATE TABLE IF NOT EXISTS master_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    field_name VARCHAR(100) NOT NULL,
    field_values TEXT,
    UNIQUE KEY uk_master_menu_field_name (field_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Car brand mapping table: Chassis code to vehicle details mapping
CREATE TABLE IF NOT EXISTS car_brand_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    car_brand VARCHAR(100) NOT NULL,
    chassis VARCHAR(50),
    car_name VARCHAR(100),
    fuel VARCHAR(50),
    wd VARCHAR(50),
    shift VARCHAR(50),
    cc INT,
    door INT,
    seat INT,
    grade VARCHAR(50),
    vehicle_type VARCHAR(100) NULL,
    `rank` VARCHAR(50) NULL,
    color VARCHAR(100) NULL,
    drive_type VARCHAR(20) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_car_brand (car_brand),
    INDEX idx_chassis (chassis),
    INDEX idx_car_name (car_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add seat column for existing installations (if car_brand_mapping table already exists)
-- MySQL doesn't support "ADD COLUMN IF NOT EXISTS", so we check information_schema first.
SET @__seat_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'car_brand_mapping'
      AND column_name = 'seat'
);
SET @__seat_alter_sql := IF(@__seat_col_exists = 0, 'ALTER TABLE car_brand_mapping ADD COLUMN seat INT', 'SELECT 1');
PREPARE seat_stmt FROM @__seat_alter_sql;
EXECUTE seat_stmt;
DEALLOCATE PREPARE seat_stmt;

-- Add vehicle_type, rank, color, drive_type for car_brand_mapping (existing installations)
SET @__vt_col := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'car_brand_mapping' AND column_name = 'vehicle_type');
SET @__vt_sql := IF(@__vt_col = 0, 'ALTER TABLE car_brand_mapping ADD COLUMN vehicle_type VARCHAR(100) NULL', 'SELECT 1');
PREPARE vt_stmt FROM @__vt_sql; EXECUTE vt_stmt; DEALLOCATE PREPARE vt_stmt;

SET @__rk_col := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'car_brand_mapping' AND column_name = 'rank');
SET @__rk_sql := IF(@__rk_col = 0, 'ALTER TABLE car_brand_mapping ADD COLUMN `rank` VARCHAR(50) NULL', 'SELECT 1');
PREPARE rk_stmt FROM @__rk_sql; EXECUTE rk_stmt; DEALLOCATE PREPARE rk_stmt;

SET @__clr_col := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'car_brand_mapping' AND column_name = 'color');
SET @__clr_sql := IF(@__clr_col = 0, 'ALTER TABLE car_brand_mapping ADD COLUMN color VARCHAR(100) NULL', 'SELECT 1');
PREPARE clr_stmt FROM @__clr_sql; EXECUTE clr_stmt; DEALLOCATE PREPARE clr_stmt;

SET @__dt_col := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'car_brand_mapping' AND column_name = 'drive_type');
SET @__dt_sql := IF(@__dt_col = 0, 'ALTER TABLE car_brand_mapping ADD COLUMN drive_type VARCHAR(20) NULL', 'SELECT 1');
PREPARE dt_stmt FROM @__dt_sql; EXECUTE dt_stmt; DEALLOCATE PREPARE dt_stmt;

-- Booking mappings table: Country/client to consignee/POD/POL mappings
CREATE TABLE IF NOT EXISTS booking_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country VARCHAR(100) NOT NULL,
    client_name VARCHAR(150),
    pod VARCHAR(120),
    stock_location VARCHAR(150),
    pols VARCHAR(255),
    consignee_name VARCHAR(255),
    consignee_address TEXT,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_country (country),
    INDEX idx_client_country (country, client_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Rixo prices table: Auction house pricing data
CREATE TABLE IF NOT EXISTS rixo_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auction_name VARCHAR(255) NOT NULL,
    type_of_vehicle VARCHAR(255),
    stock_location VARCHAR(255) NOT NULL,
    rixo_company VARCHAR(255) NOT NULL,
    venue_id VARCHAR(255),
    rixo_price VARCHAR(255),
    pol VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===========================================
-- ESSENTIAL SEED DATA
-- ===========================================
-- The following INSERT statements are REQUIRED for the application to function.
-- DO NOT REMOVE these unless you understand the consequences.

-- ---------------------------------------------
-- USERS: Default admin account
-- REQUIRED: Without this, no one can log into the system initially
-- Password: password (BCrypt hashed)
-- ---------------------------------------------
INSERT IGNORE INTO users (email, name, password_hash, role, created_at) VALUES
('admin@automan.com', 'System Administrator', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'ADMIN', NOW());

-- ---------------------------------------------
-- MASTER_MENU: Form dropdown values
-- REQUIRED: All form dropdowns (clients, countries, suppliers, etc.) 
-- pull their options from this table. Without this data, all dropdowns
-- will be empty and users cannot select values.
-- ---------------------------------------------
REPLACE INTO master_menu (field_name, field_values) VALUES
('clients', 'SHEHROZE MOTORS,DAAVI AUTO,NEW GRAND AUTO (JAWAD),IRSHAD ALI AKHTAR,AAMIR DEDHI,AUTOHANDLER,ESSA ADMANI,IRFAN MEMON HYDERABAD,NAVEES AHMAD,NIPPON TRADING INTERNATIONAL ISHRAT HUSSEIN,ARYAN MOTORS (NIRIANDER),SUSHIL KUMAR,TARIQ BUDHANI,LAKHANI MOTORS UGANDA,LAKHANI MOTORS DUBAI,LAKHANI MOTORS KENYA,ADEENA AUTO,HARIS VAYANI,CROWN EAGLE (KARAVAN MOTORS),LOCAL,OFFICE USE'),
('consignee', 'LAKHANI MOTORS (K) LTD,LAKHANI MOTORS FZE,OVERSEAS TRANSIT AGENCY (PVT) LTD'),
('country', 'Japan,Kenya,MOZAMBIQUE,NEWZEALAND,PAKISTAN,SOUTH AFRICA,UAE,Uganda,UK'),
('supplier', 'ARAI BAYSIDE,ARAI BAYSIDE (FUKUOKA YARD),ARAI BAYSIDE (DAI 2 YARD),ARAI OYAMA,ARAI SENDAI,AUCNETVAA (KISARAZU),AUCNETVAA (SAKURA),BAYAUC,CAA CHUBU,CAA GIFU,CAA TOHOKU,CAA TOKYO,HAA KOBE,HAA KOBE (SHIKOKU),HERO,HONDA HOKKAIDO,HONDA KANSAI,HONDA KYUSHU,HONDA NAGOYA,HONDA SENDAI,HONDA TOKYO,IAA OSAKA,ISUZU KOBE,ISUZU KOBE (SAKURAI YARD),ISUZU KYUSHU,ISUZU TOKYO,JAA,JU AICHI,JU AOMORI,JU CHIBA,JU FUKUI,JU FUKUOKA,JU FUKUSHIMA,JU GIFU,JU GUNMA,JU HIROSHIMA,JU HOKKAIDO,JU IBARAKI,JU ISHIKAWA,JU KANAGAWA,JU KUMAMOTO,JU MIE,JU MIYAGI,JU MIYAZAKI,JU NAGANO,JU NAGASAKI,JU NARA,JU NIIGATA,JU OITA,JU OKINAWA,JU SAITAMA,JU SHIMANE,JU SHIZUOKA,JU TOCHIGI,JU TOKYO,JU TOYAMA,JU YAMAGATA,JU YAMAGUCHI,JU YAMANASHI,KCAA FUKUOKA,KCAA KYOTO,KCAA MINAMI KYUSHU,KCAA YAMAGUCHI,LAA OKAYAMA,LAA SHIKOKU,LUM FUKUOKA,LUM HOKKAIDO,LUM KOBE,LUM KOBE (HIROSHIMA),LUM NAGOYA,LUM NAGOYA (KANAZAWA),LUM TOKYO,LUM TOKYO (SENDAI),MIRIVE AICHI,MIRIVE OSAKA,MIRIVE SAITAMA,NAA FUKUOKA,NAA NAGOYA,NAA NAGOYA (HOKURIKU),NAA OSAKA,NAA TOKYO,NOAA,NPS FUKUOKA,NPS OSAKA,NPS SENDAI,NPS TOCHIGI,NPS TOKYO,NPS TOMAKOMAI,ORIX ATSUGI,ORIX ATSUGI (OYAMA),ORIX FUKUOKA,ORIX KOBE,ORIX SENDAI,SAA HAMAMATSU,SAA SAPPORO,TAA CHUBU,TAA CHUBU (HOKURIKU),TAA CHUBU (SHIZUOKA),TAA HIROSHIMA,TAA HOKKAIDO,TAA HYOGO,TAA KANTO,TAA KANTO (KITA KANTO),TAA KANTO (SAITAMA),TAA KANTO (TAMA),TAA KINKI,TAA KINKI (SHIGA YARD),TAA KYUSHU,TAA MINAMI KYUSHU,TAA SHIKOKU,TAA SHIKOKU (EHIME),TAA TOHOKU,TAA TOHOKU (MIYAGI),TAA YOKOHAMA,TAA YOKOHAMA (ATSUGI),USS FUKUOKA,USS GUNMA,USS HOKURIKU,USS KOBE,USS KYUSHU,USS NAGOYA,USS NIIGATA,USS OKAYAMA,USS OSAKA,USS R-NAGOYA,USS SAITAMA,USS SAPPORO,USS SHIZUOKA,USS TOHOKU,USS TOKYO,USS YOKOHAMA,ZERO CHIBA,ZERO HAKATA,ZERO HOKKAIDO,ZERO OSAKA,ZERO SENDAI,ZERO SHONAN,ZIP OSAKA,ZIP TOKYO'),
('rixo_company', 'HIDA,KLC,LOGICO,SHAHBAZ,STYLISH AUTO,TAA,Y''S,YAMAZAKI'),
('stock_location', 'AQUA LOGISTICS,ECL KOBE,GLOBAL HAKATA,GLOBAL KAWASAKI,GLOBAL NAGOYA,KLC,FLASHRISE,BARAKI PARKING,LOCAL'),
('pol', 'YOKOHAMA,NAGOYA,OSAKA,SENBOKU,KOBE,HAKATA'),
('pod', 'KARACHI-PAKISTAN,MOMBASA-KENYA,UGANDA,MAPUTO-MOZAMBIQUE,JABEL ALI-DUBAI,DURBAN-SOUTH AFRICA,UK,NEWZEALAND,LOCAL-JAPAN'),
('repair_company', ''),
('car_brands', 'Toyota,Nissan,Subaru,Honda,Suzuki,Isuzu,Daihatsu,Mitsuoka,Hino,Mitsubishi Fuso,Mitsubishi,Lexus,Mazda,Nissan Diesel,Cadillac,Chevrolet,GMC,Hummer,Lincoln,Ford,Chrisler,Chrisler Jeep,Dodge,Infiniti,Acura,Tesla,Mercedes Benz,MB AMG,Smart,BMW,Audi,VolksWagen,Porsche,Rolls-Royce,Bentely,Jaguar,Land Rover,Mini,Lotus,Aston Martin,McLaren,Fiat,Ferrari,Lancia,Alfa Romeo,Maserati,Lamborghini,Abarth,Renault,Peugeot,Citroen,Ds Automobiles,Volvo,Hyundai,Kia,BYD,TOMMYKAIRA'),
('fuel', 'GASOLINE,DIESEL,HYBRID,CNG,EV,HYDROGEN,PHEV'),
('car_grade', 'G,S,Z,OPEN DECK,S X VER,S KIRAMEKI,S-T'),
('shift', 'AT,MT,6F,5F'),
('type_of_vehicle', 'PASSENGER CAR,CAR,BUS,TRUCK,MACHINERY'),
('bank_accounts', 'BANK OF SMBC MITSUI SUMITOMO (GYOUTOKU) BRANCH - 0398932 (ORDINARY) - MEMON CO., LTD. - SMBCJPJT; MUFG BANK LTD (GYOUTOKU BRANCH) - 1293891 - MEMONCO.LTD. - BOTKJPJT'),
('venue_id', '1564,3732,7378,11390,16845,20558,22431,24016,27791,30617,37488,41452,45522,45548,50354,53143,57455,59077,59160,63257,65010,70496,70506,70513,70519,77510,80521,80536,80548,88472,90471,90532,95518,126589,130528,200539,300541,316009,600525,700485,710596,900513,1355400,9733100,50000052,00S7784,A052166,B2B901,E0483,E0484,J2671,T008288,Z289700');

-- ---------------------------------------------
-- BOOKING_MAPPINGS: Consignee/POD/POL auto-fill data
-- REQUIRED: When users select a country or client on the booking page,
-- the system looks up this table to auto-fill consignee, POD, POL, and
-- stock location. Without this data, auto-fill will not work.
-- ---------------------------------------------

-- Country-level defaults (POD and Consignee)
INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES ('PAKISTAN', 'KARACHI', 'OVERSEAS TRANSIT AGENCY (PVT) LTD.', '1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI');

INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES ('KENYA', 'MOMBASA', 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES ('SOUTH AFRICA', 'DURBAN', 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES ('MOZAMBIQUE', 'MAPUTO', 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES ('UGANDA', NULL, 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES ('UAE', 'JABEL ALI-DUBAI', 'LAKHANI MOTORS FZE', 'SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com');

-- Client-specific overrides
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('PAKISTAN', 'SHEHROZE MOTORS', 'KARACHI', 'GLOBAL KAWASAKI', 'YOKOHAMA', 'OVERSEAS TRANSIT AGENCY (PVT) LTD.', '1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI');

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('KENYA', 'DAAVI AUTO', 'MOMBASA', 'AQUA LOGISTICS', 'YOKOHAMA', 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('UGANDA', 'NEW GRAND AUTO (JAWAD)', NULL, 'GLOBAL NAGOYA', 'NAGOYA', 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('MOZAMBIQUE', 'IRSHAD ALI AKHTAR', 'MAPUTO', 'FLASHRISE', 'NAGOYA', 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('UAE', 'AAMIR DEDHI', 'JABEL ALI-DUBAI', 'KLC', 'OSAKA,SENBOKU,KOBE', 'LAKHANI MOTORS FZE', 'SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com');

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('SOUTH AFRICA', 'AUTOHANDLER', 'DURBAN', 'GLOBAL HAKATA', 'HAKATA', 'LAKHANI MOTORS (K) LTD', 'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM');

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('UK', 'ESSA ADMANI', NULL, 'BARAKI PARKING', NULL, NULL, NULL);

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('NEWZEALAND', 'IRFAN MEMON HYDERABAD', NULL, 'LOCAL', NULL, NULL, NULL);

INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('JAPAN', 'NAVEES AHMAD', NULL, NULL, NULL, NULL, NULL);

-- Stock location to POL canonical mappings
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'GLOBAL KAWASAKI', 'YOKOHAMA', NULL, NULL);
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'AQUA LOGISTICS', 'YOKOHAMA', NULL, NULL);
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'GLOBAL NAGOYA', 'NAGOYA', NULL, NULL);
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'FLASHRISE', 'NAGOYA', NULL, NULL);
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'KLC', 'OSAKA,SENBOKU,KOBE', NULL, NULL);
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'GLOBAL HAKATA', 'HAKATA', NULL, NULL);
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'BARAKI PARKING', NULL, NULL, NULL);
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES ('STOCK_LOCATION_POL', NULL, NULL, 'LOCAL', NULL, NULL, NULL);

-- ---------------------------------------------
-- CAR_BRAND_MAPPING: Chassis code to vehicle details (228 rows)
-- REQUIRED: When users enter a chassis number, the system looks up
-- the first characters against this table to auto-fill brand, car name,
-- fuel type, WD, CC, door count, and grade. Without this data,
-- chassis auto-fill will not work.
-- ---------------------------------------------
INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES
('TOYOTA', 'ZN6', '86', 'GASOLINE', '2WD', NULL, 2000, 2, 'G'),
('TOYOTA', 'NCP30', 'bB', 'GASOLINE', '2WD', NULL, 1300, 5, 'S'),
('TOYOTA', 'NCP31', 'bB', 'GASOLINE', '2WD', NULL, 1500, 5, 'Z'),
('TOYOTA', 'NCP34', 'bB', 'GASOLINE', '2WD', NULL, 1500, 2, 'OPEN DECK'),
('TOYOTA', 'NCP35', 'bB', 'GASOLINE', '4WD', NULL, 1500, 5, 'Z'),
('TOYOTA', 'QNC20', 'bB', 'GASOLINE', '2WD', NULL, 1300, 5, 'S X VER'),
('TOYOTA', 'QNC21', 'bB', 'GASOLINE', '2WD', NULL, 1300, 5, 'S KIRAMEKI'),
('TOYOTA', 'QNC25', 'bB', 'GASOLINE', '4WD', NULL, 1300, 5, 'S'),
('TOYOTA', 'NGX10', 'C-HR', 'GASOLINE', '2WD', NULL, 1200, 5, 'S-T'),
('TOYOTA', 'NGX50', 'C-HR', 'GASOLINE', '4WD', NULL, 1200, 5, 'S-T'),
('TOYOTA', 'ZYX10', 'C-HR', 'HYBRID', '2WD', NULL, 1800, 5, 'S'),
('TOYOTA', 'ZYX11', 'C-HR', 'HYBRID', '2WD', NULL, 1800, 5, 'S'),
('TOYOTA', 'ACA21', 'RAV4', 'GASOLINE', '4WD', NULL, 2000, 5, 'X'),
('TOYOTA', 'ACA31', 'RAV4', 'GASOLINE', '4WD', NULL, 2400, 5, 'X'),
('TOYOTA', 'ACA36', 'RAV4', 'GASOLINE', '2WD', NULL, 2400, 5, 'X'),
('TOYOTA', 'AXAH52', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AXAH54', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MXAA52', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MXAA54', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SXA10', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SXA11', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SXA15', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZCA26', 'RAV4', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANM10', 'ISIS', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANM15', 'ISIS', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZGM10', 'ISIS', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZGM11', 'ISIS', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZGM15', 'ISIS', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZNM10', 'ISIS', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MXPK10', 'AQUA', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MXPK11', 'AQUA', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MXPK16', 'AQUA', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NHP10', 'AQUA', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AZT240', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZT240', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZT260', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRT260', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRT261', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRT265', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZT240', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZT245', 'ALLION', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANH10', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANH15', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MNH10', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MNH15', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AGH30', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AGH35', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AGH40', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AGH45', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANH20', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANH25', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH20', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH25', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH30', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH35', 'ALPHARD', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AAHP45', 'ALPHARD PHEV', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AAHH40', 'ALPHARD HV', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AAHH45', 'ALPHARD HV', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ATH10', 'ALPHARD HV', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ATH20', 'ALPHARD HV', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AYH30', 'ALPHARD HV', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE121', 'ALLEX', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE124', 'ALLEX', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZE122', 'ALLEX', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZE123', 'ALLEX', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZE124', 'ALLEX', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP110', 'IST', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP115', 'IST', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP60', 'IST', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP61', 'IST', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP65', 'IST', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZSP110', 'IST', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACM21', 'IPSUM', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACM26', 'IPSUM', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SXM10', 'IPSUM', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SXM15', 'IPSUM', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANE10', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANE11', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZGE20', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZGE21', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZGE22', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZGE25', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZNE10', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZNE14', 'WISH', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'KSP130', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'KSP90', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP10', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP13', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NC131', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP15', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP91', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NCP95', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NHP130', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NSP130', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NSP131', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NSP135', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SCP10', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SCP13', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SCP90', 'VITZ', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AGH30', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AGH35', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANH20', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ANH25', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH20', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH25', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH30', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GGH35', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TAHA40', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TAHA45', 'VELLFIRE', 'GASOLINE', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AAHP45W', 'VELLFIRE', 'PHEV', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AAHH40', 'VELLFIRE', 'HYBRID', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AAHH45', 'VELLFIRE', 'HYBRID', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ATH20', 'VELLFIRE', 'HYBRID', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AYH30', 'VELLFIRE', 'HYBRID', NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AZR60', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AZR65', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MZRA90', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MZRA92', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MZRA95', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRR70', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRR75', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRR80', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRR85', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWR80', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWR90', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWR92', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWR95', 'VOXY', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRR80', 'ESQUIRE', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRR85', 'ESQUIRE', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWR80', 'ESQUIRE', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACR30', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACR40', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACR50', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACR55', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GSR50', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'GSR55', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MCR30', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MCR40', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR10', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR11', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR20', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR21', 'ESTIMA', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'CXR10', 'Toyota Estima Emina', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'CXR20', 'Toyota Estima Emina', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR10', 'Toyota Estima Emina', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR20', 'Toyota Estima Emina', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR21', 'Toyota Estima Emina', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR10', 'Toyota Estima Lucida', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR11', 'Toyota Estima Lucida', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR20', 'Toyota Estima Lucida', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'TCR21', 'Toyota Estima Lucida', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AHR10', 'Toyota Estima Hybrid', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AHR20', 'Toyota Estima Hybrid', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NRE185', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE151', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE154', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE181', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE184', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRE152', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRE154', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRE186', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWE186', 'Toyota Auris', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACV30', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACV35', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACV40', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ACV45', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AVV50', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AXVH70', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AXVH75', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SV22', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SV30', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SV32', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SV40', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SV41', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SXV20', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'SXV25', 'Toyota Camry', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE100', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE101', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE110', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE111', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE114', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE91', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE92', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'CE100', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'CE104', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'CE110', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'CE113', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'CE114', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'EE111', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'KE10', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'KE11', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'KE15', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'KE20', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MZEA17', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NRE210', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE120', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE121', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE124', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRE212', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWE211', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWE214', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWE215', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZWE219', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZE122', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZE124', 'Toyota Corolla', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NKE165', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NRE160', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NRE161', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE141', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE144', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE161', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE164', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRE142', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZRE144', 'Toyota Corolla Axio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MXGA10', 'Toyota Corolla Cross', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'MXGH15', 'Toyota Corolla Cross', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZSG10', 'Toyota Corolla Cross', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZVG11', 'Toyota Corolla Cross', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZVG13', 'Toyota Corolla Cross', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZVG15', 'Toyota Corolla Cross', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZVG16', 'Toyota Corolla Cross', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE111', 'Toyota Corolla Spacio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'AE115', 'Toyota Corolla Spacio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'NZE121', 'Toyota Corolla Spacio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZE122', 'Toyota Corolla Spacio', NULL, NULL, NULL, NULL, NULL, NULL),
('TOYOTA', 'ZZE124', 'Toyota Corolla Spacio', NULL, NULL, NULL, NULL, NULL, NULL);

-- ---------------------------------------------
-- RIXO_PRICES: Auction house pricing data (164 rows)
-- REQUIRED: When users select an auction house and vehicle type,
-- the system looks up this table to auto-fill the Rixo price,
-- stock location, Rixo company, and venue ID. Without this data,
-- price lookup will not work.
-- ---------------------------------------------
INSERT INTO rixo_prices (auction_name, type_of_vehicle, stock_location, rixo_company, venue_id, rixo_price) VALUES
('AUCNETVAA (KISARAZU)', 'CAR, TRUCK', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'A052166', '8000'),
('AUCNETVAA (SAKURA)', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'A052166', '8000'),
('HONDA TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '1355400', '8000'),
('HONDA KANSAI', 'Car', 'KLC', 'KLC', '1355400', '5500'),
('HONDA NAGOYA', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '1355400', '5000'),
('HONDA KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', '1355400', '6820'),
('HONDA SENDAI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '1355400', '19800'),
('HONDA HOKKAIDO', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '1355400', '33600'),
('JU TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '20558', '6000'),
('NOAA', 'Car', 'KLC', 'KLC', 'Z289700', '5500'),
('CAA TOKYO', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'T008288', '6000'),
('CAA TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'T008288', '7000'),
('CAA GIFU', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'T008288', '7500'),
('CAA TOHOKU', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'T008288', '28200'),
('TAA KINKI', 'Truck', 'KLC', 'KLC', '65010', '9500'),
('TAA KINKI (SHIGA YARD)', 'Car', 'KLC', 'KLC', '65010', '18000'),
('TAA KYUSHU', 'Truck', 'GLOBAL HAKATA', 'Y''S', '65010', '4620'),
('TAA MINAMI KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', '65010', '15400'),
('TAA HIROSHIMA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '65010', '12000'),
('TAA SHIKOKU', 'Car', 'KLC', 'KLC', '65010', '15000'),
('TAA SHIKOKU (EHIME)', 'Truck', 'KLC', 'TAA', '65010', '20000'),
('JU SAITAMA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '16845', '7000'),
('JU SAITAMA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '16845', '7000'),
('JU SHIZUOKA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '900513', '14000'),
('JU NAGANO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '80536', '26400'),
('JU MIE', 'Car', 'KLC', 'KLC', '316009', ''),
('JU YAMAGUCHI', 'Truck', 'GLOBAL HAKATA', 'Y''S', '59160', ''),
('JU AOMORI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '200539', '52400'),
('JU FUKUI', 'Truck', '-', '-', '126589', ''),
('NAA FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', '9733100', '3900'),
('SAA SAPPORO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '57455', '34600'),
('ORIX KOBE', 'Car', 'KLC', 'KLC', '50000052', '5500'),
('ORIX FUKUOKA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '50000052', '3900'),
('ORIX SENDAI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '50000052', '19800'),
('NPS TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '7378', '7000'),
('NPS OSAKA', 'Car', 'KLC', 'KLC', '7378', '3800'),
('NPS SENDAI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '7378', '19800'),
('NPS FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', '7378', ''),
('NPS TOCHIGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '7378', '13200'),
('NPS TOMAKOMAI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '7378', ''),
('LUM NAGOYA', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', '1564', '6000'),
('LUM FUKUOKA', 'Car', 'GLOBAL HAKATA', 'LOGICO', '1564', '6200'),
('USS YOKOHAMA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'E0483', '4000'),
('USS YOKOHAMA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '3500'),
('USS YOKOHAMA', 'TRUCKS BUS', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '8000'),
('USS R-NAGOYA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '5000'),
('CAA CHUBU', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'T008288', '6000'),
('BAYAUC', 'Car', 'KLC', 'KLC', '24016', '5000'),
('IAA OSAKA', 'Truck', 'KLC', 'KLC', '27791', '3800'),
('LAA SHIKOKU', 'Car', 'KLC', 'KLC', '00S7784', '15000'),
('HERO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '30617', '10000'),
('KCAA MINAMI KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', 'J2671', '14300'),
('KCAA KYOTO', 'Truck', 'KLC', 'KLC', 'J2671', '9500'),
('ISUZU TOKYO', 'Car', 'GLOBAL KAWASAKI', '-', 'A052166', ''),
('MIRIVE SAITAMA', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '710596', '10800'),
('JU IBARAKI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '80548', '14400'),
('JU ISHIKAWA', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', '70496', '16600'),
('JU KUMAMOTO', 'Car', 'GLOBAL HAKATA', 'Y''S', '70513', '12100'),
('JU OITA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '45522', ''),
('JU NAGASAKI', 'Car', 'GLOBAL HAKATA', 'Y''S', 'A052166', ''),
('ORIX ATSUGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '50000052', '8900'),
('ORIX ATSUGI (OYAMA)', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '50000052', '13200'),
('LUM TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '1564', '9600'),
('LUM TOKYO (SENDAI)', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '1564', '19800'),
('USS SAPPORO', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '34600'),
('USS TOHOKU', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '19400'),
('USS NIIGATA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '22200'),
('USS KOBE', 'Truck', 'KLC', 'KLC', 'E0483', '5500'),
('USS FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', 'E0483', '4620'),
('JAA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '11390', '6000'),
('JAA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '11390', '6000'),
('TAA CHUBU', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '65010', '5000'),
('TAA CHUBU (SHIZUOKA)', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '65010', '13000'),
('TAA CHUBU (HOKURIKU)', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '65010', '18000'),
('TAA CHUBU (HOKURIKU)', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', '65010', '16600'),
('TAA KANTO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '65010', '6000'),
('TAA KANTO', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '6000'),
('TAA KANTO', 'BIG CAR/TRUCK', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '8000'),
('TAA KANTO (KITA KANTO)', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '12000'),
('TAA KANTO (SAITAMA)', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '65010', '7000'),
('TAA KANTO (SAITAMA)', 'Car', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '8000'),
('TAA KANTO (TAMA)', 'Truck', 'GLOBAL KAWASAKI', 'TAA', '65010', '12000'),
('TAA TOHOKU', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '18000'),
('TAA TOHOKU (MIYAGI)', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '19800'),
('TAA HOKKAIDO', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '34600'),
('KCAA FUKUOKA', 'Truck', 'GLOBAL HAKATA', 'Y''S', 'J2671', '3900'),
('MIRIVE OSAKA', 'Car', 'KLC', 'KLC', '710596', '6000'),
('ARAI OYAMA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '22431', '10000'),
('ARAI OYAMA', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '10000'),
('ARAI OYAMA', 'HIACE', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '12000'),
('ARAI OYAMA', 'TRUCKS', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '18000'),
('NAA NAGOYA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '9733100', '7000'),
('NAA NAGOYA (HOKURIKU)', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '9733100', '28000'),
('NAA NAGOYA (HOKURIKU)', 'Car', 'GLOBAL NAGOYA', 'HIDA', '9733100', '15000'),
('NAA OSAKA', 'Truck', 'KLC', 'KLC', '9733100', '5500'),
('JU AICHI', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '95518', '4000'),
('JU HIROSHIMA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '77510', '14300'),
('JU FUKUSHIMA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '88472', '18000'),
('JU GUNMA', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '700485', '12000'),
('JU KANAGAWA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '90471', '12000'),
('JU KANAGAWA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '90471', ''),
('JU TOYAMA', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '600525', ''),
('JU TOYAMA', 'Car', 'GLOBAL NAGOYA', 'LOGICO', '600525', '18100'),
('JU MIYAZAKI', 'Truck', 'GLOBAL HAKATA', 'Y''S', '70519', ''),
('ZIP OSAKA', 'Car', 'KLC', 'KLC', '41452', '5500'),
('SAA HAMAMATSU', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '3732', '10000'),
('ISUZU KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', 'A052166', '4400'),
('LUM HOKKAIDO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '1564', '33600'),
('LUM KOBE', 'Car', 'KLC', 'LOGICO', '1564', '6500'),
('LUM KOBE (HIROSHIMA)', 'Truck', 'GLOBAL HAKATA', 'LOGICO', '1564', '30600'),
('ZERO SHONAN', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'B2B901', '8900'),
('ZERO OSAKA', 'Truck', 'KLC', 'KLC', 'B2B901', ''),
('USS TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'E0483', '7000'),
('USS TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '7000'),
('USS TOKYO', 'G CLASS/ LAND CRUISER/', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '10000'),
('USS TOKYO', 'TRUCKS', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '15000'),
('JU FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', '37488', '3900'),
('JU MIYAGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '70506', '19400'),
('JU CHIBA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '59077', '8000'),
('JU CHIBA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '59077', '7000'),
('JU NIIGATA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '45548', '22200'),
('JU TOCHIGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '80521', '13200'),
('JU OKINAWA', 'Car', '-', '-', '53143', ''),
('JU HOKKAIDO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '63257', '34600'),
('JU SHIMANE', 'Car', '-', '-', 'A052166', ''),
('ARAI BAYSIDE', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '22431', '3000'),
('ARAI BAYSIDE', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '3000'),
('ARAI BAYSIDE (DAI 2 YARD)', 'Car', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '3500'),
('ARAI SENDAI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '22431', '19800'),
('LAA OKAYAMA', 'Car', 'KLC', 'KLC', '00S7784', '10800'),
('NAA TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'YAMAZAKI', '9733100', '4000'),
('NAA TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', '9733100', '3500'),
('KCAA YAMAGUCHI', 'Truck', 'GLOBAL HAKATA', 'Y''S', 'J2671', '10200'),
('ISUZU KOBE', 'Car', 'KLC', 'KLC', 'A052166', '14000'),
('ISUZU KOBE (SAKURAI YARD)', 'Truck', 'KLC', 'KLC', 'A052166', '28000'),
('MIRIVE AICHI', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '710596', '6000'),
('ZERO HOKKAIDO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', 'B2B901', '33600'),
('ZERO SENDAI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'B2B901', '19800'),
('ZERO CHIBA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'B2B901', '7000'),
('ZERO HAKATA', 'Car', 'GLOBAL HAKATA', 'Y''S', 'B2B901', '7200'),
('USS SAITAMA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'E0483', '8000'),
('USS NAGOYA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '5000'),
('USS OSAKA', 'Truck', 'KLC', 'KLC', 'E0483', '5500'),
('JU GIFU', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '50354', '7500'),
('JU NARA', 'Truck', 'KLC', 'KLC', '130528', '9500'),
('JU YAMAGATA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '90532', ''),
('JU YAMANASHI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '300541', '21600'),
('TAA YOKOHAMA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '65010', '9000'),
('TAA YOKOHAMA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '3500'),
('TAA YOKOHAMA (ATSUGI)', 'Car', 'GLOBAL KAWASAKI', 'TAA', '65010', '9900'),
('TAA HYOGO', 'Truck', 'KLC', 'KLC', '65010', '5500'),
('ZIP TOKYO', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '41452', '8000'),
('ZIP TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '41452', '6000'),
('USS GUNMA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '13200'),
('USS HOKURIKU', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', 'E0483', '19800'),
('USS HOKURIKU', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '18000'),
('USS SHIZUOKA', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '13000'),
('USS OKAYAMA', 'Car', 'KLC', 'KLC', 'E0483', '10800'),
('USS KYUSHU', 'Truck', 'GLOBAL HAKATA', 'Y''S', 'E0483', '4620'),
('HAA KOBE', 'Car', 'KLC', 'KLC', 'E0483', '5500'),
('HAA KOBE', 'Truck', 'ECL KOBE', 'KLC', 'E0483', '4500');

-- POL: populate from stock_location mapping (see mapping table: GLOBAL KAWASAKI->YOKOHAMA, AQUA LOGISTICS->YOKOHAMA, GLOBAL NAGOYA->NAGOYA, FLASHRISE->NAGOYA, KLC->OSAKA, GLOBAL HAKATA->HAKATA, BARAKI PARKING->---, LOCAL->---)
UPDATE rixo_prices SET pol = CASE
    WHEN stock_location LIKE 'GLOBAL KAWASAKI%' THEN 'YOKOHAMA'
    WHEN stock_location = 'AQUA LOGISTICS' THEN 'YOKOHAMA'
    WHEN stock_location LIKE 'GLOBAL NAGOYA%' THEN 'NAGOYA'
    WHEN stock_location = 'FLASHRISE' THEN 'NAGOYA'
    WHEN stock_location = 'KLC' THEN 'OSAKA'
    WHEN stock_location LIKE 'GLOBAL HAKATA%' THEN 'HAKATA'
    WHEN stock_location = 'BARAKI PARKING' THEN '---'
    WHEN stock_location = 'LOCAL' THEN '---'
    ELSE NULL
END
WHERE pol IS NULL OR pol = '';

-- For existing databases created before POL column existed, run once:
-- ALTER TABLE rixo_prices ADD COLUMN pol VARCHAR(255) NULL AFTER rixo_price;
-- Then run the UPDATE above to backfill POL from stock_location.

-- ---------------------------------------------
-- RIXO_MAPPING: exact values from RIXO_mapping.csv
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS rixo_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rixo_company VARCHAR(255) NOT NULL,
    stock_location VARCHAR(255) NOT NULL,
    supported_vehicle_type VARCHAR(255) NULL,
    rixo_price VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELETE FROM rixo_mapping;

INSERT INTO rixo_mapping (rixo_company, stock_location, supported_vehicle_type, rixo_price) VALUES
('LOGICO', 'GLOBAL HAKATA', NULL, '¥6,200'),
('LOGICO', 'GLOBAL HAKATA', NULL, '¥30,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥33,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥9,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,800'),
('LOGICO', 'GLOBAL NAGOYA', NULL, '¥6,000'),
('LOGICO', 'GLOBAL NAGOYA', NULL, '¥19,200'),
('LOGICO', 'KLC', NULL, '¥6,500'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥10,000'),
('Y''S', 'GLOBAL HAKATA', NULL, NULL),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,800'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥13,200'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, NULL),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥7,000'),
('KLC', 'KLC', NULL, '¥3,800'),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, '¥6,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥6,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, '¥7,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥7,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥6,000'),
('Y''S', 'GLOBAL HAKATA', 'CAR', '¥4,200'),
('LOGICO', 'GLOBAL KAWASAKI', '-', '¥19,800'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'CAR', '¥3,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'CAR', '¥3,500'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'CAR', '¥10,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'HIACE', '¥12,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'TRUCK', '¥18,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', 'CAR', '¥3,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', 'CAR', '¥10,000'),
('KLC', 'KLC', 'CAR / BIG CAR', '¥5,000'),
('KLC', 'KLC', NULL, '¥3,800'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥10,000'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥3,900'),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, '¥6,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥8,000'),
('KLC', 'KLC', NULL, '¥5,500'),
('Y''S', 'GLOBAL HAKATA', NULL, NULL),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥22,200'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥7,500'),
('-', '-', NULL, NULL),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥34,600'),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, '¥7,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥8,000'),
('Y''S', 'GLOBAL HAKATA', NULL, NULL),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥34,600'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥12,000'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥4,620'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥15,400'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥34,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥12,000'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥18,000'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,800'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'CAR', '¥6,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'BIG CAR/TRUCK', '¥8,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, '¥8,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, '¥3,500'),
('TAA', 'GLOBAL KAWASAKI', NULL, '¥12,000'),
('TAA', 'GLOBAL KAWASAKI', NULL, '¥9,900'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥6,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥7,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥9,000'),
('LOGICO', 'GLOBAL NAGOYA', NULL, '¥16,600'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥5,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥18,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥13,000'),
('KLC', 'KLC', NULL, '¥5,500'),
('KLC', 'KLC', NULL, '¥9,500'),
('KLC', 'KLC', NULL, '¥18,000'),
('KLC', 'KLC', NULL, '¥15,000'),
('TAA', 'KLC', NULL, '¥20,000'),
('HIDA', 'GLOBAL NAGOYA', NULL, '¥15,000'),
('LOGICO', 'GLOBAL NAGOYA', NULL, '¥16,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,400'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥12,100'),
('Y''S', 'GLOBAL HAKATA', NULL, NULL),
('Y''S', 'GLOBAL HAKATA', NULL, '¥14,300'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥13,200'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥26,400'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥14,400'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥18,000'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥12,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, NULL),
('LOGICO', 'GLOBAL KAWASAKI', NULL, NULL),
('STYLISH AUTO', 'GLOBAL KAWASAKI', NULL, '¥24,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥4,000'),
('-', '-', NULL, NULL),
('KLC', 'KLC', NULL, '¥9,500'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥52,400'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥21,600'),
('KLC', 'KLC', NULL, NULL),
('LOGICO', 'GLOBAL NAGOYA', NULL, '¥18,100'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, NULL),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥12,000'),
('LOGICO', 'AQUA LOGISTICS', NULL, '¥19,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥10,800'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥6,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', 'hiace commuter/ taller height', '¥9,000'),
('KLC', 'KLC', NULL, '¥6,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥15,000'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥6,820'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥33,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,800'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥8,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥5,000'),
('KLC', 'KLC', NULL, '¥5,500'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥3,900'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'CAR', '¥3,500'),
('YAMAZAKI', 'GLOBAL KAWASAKI', 'CAR', '¥4,000'),
('HIDA', 'GLOBAL NAGOYA', NULL, '¥15,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥7,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥28,000'),
('KLC', 'KLC', NULL, '¥5,500'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥3,900'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥8,900'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥13,200'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,800'),
('KLC', 'KLC', NULL, '¥5,500'),
('KLC', 'KLC', NULL, '¥10,800'),
('KLC', 'KLC', NULL, '¥15,000'),
('-', '-', NULL, NULL),
('Y''S', 'GLOBAL HAKATA', NULL, '¥4400 | ¥7700'),
('Y''S', 'GLOBAL HAKATA', NULL, NULL),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, NULL),
('YAMAZAKI', 'GLOBAL KAWASAKI', 'CAR', '¥8,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', 'CAR', '¥8,000'),
('KLC', 'KLC', NULL, '¥14,000'),
('KLC', 'KLC', NULL, '¥28,000'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥7,200'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥33,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,800'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥8,900'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥7,000'),
('KLC', 'KLC', NULL, NULL),
('KLC', 'ECL KOBE', NULL, '¥4,500'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥4,620'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥4,620'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥13,200'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥22,200'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥34,600'),
('LOGICO', 'GLOBAL KAWASAKI', NULL, '¥19,400'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'CAR', '¥7,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'G CLASS/ LAND CRUISER/', '¥10,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'TRUCKS', '¥15,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', NULL, '¥3,500'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'TRUCKS BUS ', '¥8,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥8,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', 'CAR', '¥7,000'),
('YAMAZAKI', 'GLOBAL KAWASAKI', NULL, '¥4,000'),
('LOGICO', 'GLOBAL NAGOYA', NULL, '¥19,800'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥11,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥18,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '￥5500 / ￥5000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥5,500'),
('STYLISH AUTO', 'GLOBAL NAGOYA', NULL, '¥13,000'),
('KLC', 'KLC', NULL, '¥5,500'),
('KLC', 'KLC', NULL, '¥5,500'),
('KLC', 'KLC', NULL, '¥10,800'),
('KLC', 'KLC', NULL, '¥5,500'),
('LOGICO', 'KLC', NULL, '¥6,500'),
('LOGICO', 'KLC', NULL, '¥12,500'),
('STYLISH AUTO', 'KLC', NULL, '¥6,000'),
('STYLISH AUTO', 'KLC', NULL, '¥33,000'),
('STYLISH AUTO', 'KLC', NULL, '¥12,000'),
('LOGICO', 'GLOBAL KAWASAKI', 'BUS(ROSA)', 'AROUND 130,000YEN'),
('STYLISH AUTO', 'KLC', NULL, '¥8,000'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥3,900'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥14,300'),
('Y''S', 'GLOBAL HAKATA', NULL, '¥10,200'),
('KLC', 'KLC', NULL, '¥9,500'),
('LOGICO', 'GLOBAL KAWASAKI', 'CAR / BIG CAR', '¥36,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'CAR / BIG CAR', '¥7,000'),
('SHAHBAZ', 'GLOBAL KAWASAKI', 'TRUCK', NULL),
('YAMAZAKI', 'GLOBAL KAWASAKI', 'CAR / BIG CAR', '¥6,000'),
('STYLISH AUTO', 'GLOBAL NAGOYA', 'CAR / BIG CAR', '¥6,500'),
('STYLISH AUTO', 'GLOBAL NAGOYA', 'CAR / BIG CAR', '¥7,500'),
('KLC', 'KLC', NULL, '¥5,500');

-- ===========================================
-- END OF INITIALIZATION SCRIPT
-- ===========================================
