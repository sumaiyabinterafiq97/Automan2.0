-- Rixo Prices Table Migration
-- Consolidated migration for rixo_prices table creation, data import, and updates

USE automan_car_purchase;

-- ===========================================
-- TABLE CREATION
-- ===========================================

CREATE TABLE IF NOT EXISTS rixo_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auction_name VARCHAR(255) NOT NULL,
    type_of_vehicle VARCHAR(255),
    stock_location VARCHAR(255) NOT NULL,
    rixo_company VARCHAR(255) NOT NULL,
    venue_id VARCHAR(255),
    rixo_price VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===========================================
-- COLUMN RENAMES (if needed for existing databases)
-- ===========================================

-- Handle auction_house/auction_name columns (idempotent)
SET @col_auction_house_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'rixo_prices' 
    AND COLUMN_NAME = 'auction_house');
SET @col_auction_name_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'rixo_prices' 
    AND COLUMN_NAME = 'auction_name');
-- If auction_house exists and auction_name doesn't, rename it
SET @sql = IF(@col_auction_house_exists > 0 AND @col_auction_name_exists = 0, 
    'ALTER TABLE rixo_prices CHANGE COLUMN auction_house auction_name VARCHAR(255) NOT NULL', 
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- If both exist, drop auction_house
SET @col_auction_house_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'rixo_prices' 
    AND COLUMN_NAME = 'auction_house');
SET @col_auction_name_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'rixo_prices' 
    AND COLUMN_NAME = 'auction_name');
SET @sql = IF(@col_auction_house_exists > 0 AND @col_auction_name_exists > 0, 
    'ALTER TABLE rixo_prices DROP COLUMN auction_house', 
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Rename rate to rixo_price (if column exists)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'rixo_prices' 
    AND COLUMN_NAME = 'rate');
SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE rixo_prices CHANGE COLUMN rate rixo_price VARCHAR(255)', 
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===========================================
-- DATA IMPORT
-- ===========================================

-- Clear existing data before import
DELETE FROM rixo_prices;

INSERT INTO rixo_prices (auction_name, type_of_vehicle, stock_location, rixo_company, venue_id, rixo_price) VALUES
('AUCNETVAA (KISARAZU)', 'CAR, TRUCK', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'A052166', '¥8,000'),
('AUCNETVAA (SAKURA)', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'A052166', '¥8,000'),
('HONDA TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '1355400', '¥8,000'),
('HONDA KANSAI', 'Car', 'KLC', 'KLC', '1355400', '¥5,500'),
('HONDA NAGOYA', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '1355400', '¥5,000'),
('HONDA KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥6,820'),
('HONDA SENDAI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '1355400', '¥19,800'),
('HONDA HOKKAIDO', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '1355400', '¥33,600'),
('JU TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '20558', '¥6,000'),
('NOAA', 'Car', 'KLC', 'KLC', 'Z289700', '¥5,500'),
('CAA TOKYO', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'T008288', '¥6,000'),
('CAA TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'T008288', '¥7,000'),
('CAA GIFU', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'T008288', '¥7,500'),
('CAA TOHOKU', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'T008288', '¥28,200'),
('TAA KINKI', 'Truck', 'KLC', 'KLC', '65010', '¥9,500'),
('TAA KINKI (SHIGA YARD)', 'Car', 'KLC', 'KLC', '65010', '¥18,000'),
('TAA KYUSHU', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', '¥4,620'),
('TAA MINAMI KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥15,400'),
('TAA HIROSHIMA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', '¥12,000'),
('TAA SHIKOKU', 'Car', 'KLC', 'KLC', '65010', '¥15,000'),
('TAA SHIKOKU (EHIME)', 'Truck', 'KLC', 'TAA', '65010', '¥20,000'),
('JU SAITAMA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '16845', '¥7,000'),
('JU SAITAMA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '16845', '¥7,000'),
('JU SHIZUOKA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '900513', '¥14,000'),
('JU NAGANO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '80536', '¥26,400'),
('JU MIE', 'Car', 'KLC', 'KLC', '316009', ''),
('JU YAMAGUCHI', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', ''),
('JU AOMORI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '200539', '¥52,400'),
('JU FUKUI', 'Truck', '-', '-', '126589', ''),
('NAA FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥3,900'),
('SAA SAPPORO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '57455', '¥34,600'),
('ORIX KOBE', 'Car', 'KLC', 'KLC', '50000052', '¥5,500'),
('ORIX FUKUOKA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', '¥3,900'),
('ORIX SENDAI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '50000052', '¥19,800'),
('NPS TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '7378', '¥7,000'),
('NPS OSAKA', 'Car', 'KLC', 'KLC', '7378', '¥3,800'),
('NPS SENDAI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '7378', '¥19,800'),
('NPS FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', '', ''),
('NPS TOCHIGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '7378', '¥13,200'),
('NPS TOMAKOMAI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '7378', ''),
('LUM NAGOYA', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', '1564', '¥6,000'),
('LUM FUKUOKA', 'Car', 'GLOBAL HAKATA', 'LOGICO', '1564', '¥6,200'),
('USS YOKOHAMA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'E0483', '¥4,000'),
('USS YOKOHAMA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '¥3,500'),
('USS YOKOHAMA', 'TRUCKS BUS', '', '', '', '¥8,000'),
('USS R-NAGOYA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '¥5,000'),
('CAA CHUBU', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'T008288', '¥6,000'),
('BAYAUC', 'Car', 'KLC', 'KLC', '24016', '¥5,000'),
('IAA OSAKA', 'Truck', 'KLC', 'KLC', '27791', '¥3,800'),
('LAA SHIKOKU', 'Car', 'KLC', 'KLC', '00S7784', '¥15,000'),
('HERO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '30617', '¥10,000'),
('KCAA MINAMI KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥14,300'),
('KCAA KYOTO', 'Truck', 'KLC', 'KLC', 'J2671', '¥9,500'),
('ISUZU TOKYO', 'Car', 'GLOBAL KAWASAKI', '-', '', ''),
('MIRIVE SAITAMA', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '710596', '¥10,800'),
('JU IBARAKI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '80548', '¥14,400'),
('JU ISHIKAWA', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', '70496', '¥16,600'),
('JU KUMAMOTO', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥12,100'),
('JU OITA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', ''),
('JU NAGASAKI', 'Car', 'GLOBAL HAKATA', 'Y''S', '', ''),
('ORIX ATSUGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '50000052', '¥8,900'),
('ORIX ATSUGI (OYAMA)', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '50000052', '¥13,200'),
('LUM TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '1564', '¥9,600'),
('LUM TOKYO (SENDAI)', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '1564', '¥19,800'),
('USS SAPPORO', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '¥34,600'),
('USS TOHOKU', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '¥19,400'),
('USS NIIGATA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '¥22,200'),
('USS KOBE', 'Truck', 'KLC', 'KLC', 'E0483', '¥5,500'),
('USS FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥4,620'),
('JAA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '11390', '¥6,000'),
('JAA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '11390', '¥6,000'),
('TAA CHUBU', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '65010', '¥5,000'),
('TAA CHUBU (SHIZUOKA)', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '65010', '¥13,000'),
('TAA CHUBU (HOKURIKU)', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '65010', '¥18,000'),
('TAA CHUBU (HOKURIKU)', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', '65010', '¥16,600'),
('TAA KANTO', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '65010', '¥6,000'),
('TAA KANTO', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '¥6,000'),
('TAA KANTO', 'BIG CAR/TRUCK', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '¥8,000'),
('TAA KANTO (KITA KANTO)', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '¥12,000'),
('TAA KANTO (SAITAMA)', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '65010', '¥7,000'),
('TAA KANTO (SAITAMA)', 'Car', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '¥8,000'),
('TAA KANTO (TAMA)', 'Truck', 'GLOBAL KAWASAKI', 'TAA', '65010', '¥12,000'),
('TAA TOHOKU', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '¥18,000'),
('TAA TOHOKU (MIYAGI)', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '¥19,800'),
('TAA HOKKAIDO', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '65010', '¥34,600'),
('KCAA FUKUOKA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', '¥3,900'),
('MIRIVE OSAKA', 'Car', 'KLC', 'KLC', '710596', '¥6,000'),
('ARAI OYAMA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '22431', '¥10,000'),
('ARAI OYAMA', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '¥10,000'),
('ARAI OYAMA', 'HIACE', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '¥12,000'),
('ARAI OYAMA', 'TRUCKS', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '¥18,000'),
('NAA NAGOYA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '9733100', '¥7,000'),
('NAA NAGOYA (HOKURIKU)', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '9733100', '¥28,000'),
('NAA NAGOYA (HOKURIKU)', 'Car', 'GLOBAL NAGOYA', 'HIDA', '9733100', '¥15,000'),
('NAA OSAKA', 'Truck', 'KLC', 'KLC', '9733100', '¥5,500'),
('JU AICHI', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '95518', '¥4,000'),
('JU HIROSHIMA', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', '¥14,300'),
('JU FUKUSHIMA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '88472', '¥18,000'),
('JU GUNMA', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '700485', '¥12,000'),
('JU KANAGAWA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '90471', '¥12,000'),
('JU KANAGAWA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', '90471', ''),
('JU KANAGAWA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '90471', '¥12,000'),
('JU TOYAMA', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '600525', ''),
('JU TOYAMA', 'Car', 'GLOBAL NAGOYA', 'LOGICO', '600525', '¥18,100'),
('JU MIYAZAKI', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', ''),
('ZIP OSAKA', 'Car', 'KLC', 'KLC', '41452', '¥5,500'),
('SAA HAMAMATSU', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', '3732', '¥10,000'),
('ISUZU KYUSHU', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥4400 | ¥7700'),
('LUM HOKKAIDO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '1564', '¥33,600'),
('LUM KOBE', 'Car', 'KLC', 'LOGICO', '1564', '¥6,500'),
('LUM KOBE (HIROSHIMA)', 'Truck', 'GLOBAL HAKATA', 'LOGICO', '1564', '¥30,600'),
('ZERO SHONAN', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'B2B901', '¥8,900'),
('ZERO OSAKA', 'Truck', 'KLC', 'KLC', 'B2B901', ''),
('USS TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'E0483', '¥7,000'),
('USS TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '¥7,000'),
('USS TOKYO', 'G CLASS/ LAND CRUISER/', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '¥10,000'),
('USS TOKYO', 'TRUCKS', 'GLOBAL KAWASAKI', 'SHAHBAZ', 'E0483', '¥15,000'),
('JU FUKUOKA', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥3,900'),
('JU MIYAGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '70506', '¥19,400'),
('JU CHIBA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '59077', '¥8,000'),
('JU CHIBA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '59077', '¥7,000'),
('JU NIIGATA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '45548', '¥22,200'),
('JU TOCHIGI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '80521', '¥13,200'),
('JU OKINAWA', 'Car', '-', '-', '53143', ''),
('JU HOKKAIDO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '63257', '¥34,600'),
('JU SHIMANE', 'Car', '-', '-', 'A052166', ''),
('ARAI BAYSIDE', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '22431', '¥3,000'),
('ARAI BAYSIDE', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '¥3,000'),
('ARAI BAYSIDE (DAI 2 YARD)', 'Car', 'GLOBAL KAWASAKI', 'SHAHBAZ', '22431', '¥3,500'),
('ARAI SENDAI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '22431', '¥19,800'),
('LAA OKAYAMA', 'Car', 'KLC', 'KLC', '00S7784', '¥10,800'),
('NAA TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'YAMAZAKI', '9733100', '¥4,000'),
('NAA TOKYO', 'CAR', 'GLOBAL KAWASAKI', 'SHAHBAZ', '9733100', '¥3,500'),
('KCAA YAMAGUCHI', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', '¥10,200'),
('ISUZU KOBE', 'Car', 'KLC', 'KLC', 'A052166', '¥14,000'),
('ISUZU KOBE (SAKURAI YARD)', 'Truck', 'KLC', 'KLC', 'A052166', '¥28,000'),
('MIRIVE AICHI', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '710596', '¥6,000'),
('ZERO HOKKAIDO', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', 'B2B901', '¥33,600'),
('ZERO SENDAI', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'B2B901', '¥19,800'),
('ZERO CHIBA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'B2B901', '¥7,000'),
('ZERO HAKATA', 'Car', 'GLOBAL HAKATA', 'Y''S', '', '¥7,200'),
('USS SAITAMA', 'Truck', 'GLOBAL KAWASAKI', 'YAMAZAKI', 'E0483', '¥8,000'),
('USS NAGOYA', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '¥5,000'),
('USS OSAKA', 'Truck', 'KLC', 'KLC', 'E0483', '¥5,500'),
('JU GIFU', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', '50354', '¥7,500'),
('JU NARA', 'Truck', 'KLC', 'KLC', '130528', '¥9,500'),
('JU YAMAGATA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', '90532', ''),
('JU YAMANASHI', 'Truck', 'GLOBAL KAWASAKI', 'LOGICO', '300541', '¥21,600'),
('TAA YOKOHAMA', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '65010', '¥9,000'),
('TAA YOKOHAMA', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '65010', '¥3,500'),
('TAA YOKOHAMA (ATSUGI)', 'Car', 'GLOBAL KAWASAKI', 'TAA', '65010', '¥9,900'),
('TAA HYOGO', 'Truck', 'KLC', 'KLC', '65010', '¥5,500'),
('ZIP TOKYO', 'Car', 'GLOBAL KAWASAKI', 'YAMAZAKI', '41452', '¥8,000'),
('ZIP TOKYO', 'Truck', 'GLOBAL KAWASAKI', 'SHAHBAZ', '41452', '¥6,000'),
('USS GUNMA', 'Car', 'GLOBAL KAWASAKI', 'LOGICO', 'E0483', '¥13,200'),
('USS HOKURIKU', 'Truck', 'GLOBAL NAGOYA', 'LOGICO', 'E0483', '¥19,800'),
('USS HOKURIKU', 'Car', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '¥18,000'),
('USS SHIZUOKA', 'Truck', 'GLOBAL NAGOYA', 'STYLISH AUTO', 'E0483', '¥13,000'),
('USS OKAYAMA', 'Car', 'KLC', 'KLC', 'E0483', '¥10,800'),
('USS KYUSHU', 'Truck', 'GLOBAL HAKATA', 'Y''S', '', '¥4,620'),
('HAA KOBE', 'Car', 'KLC', 'KLC', 'E0483', '¥5,500'),
('HAA KOBE', 'Truck', 'ECL KOBE', 'KLC', 'E0483', '¥4,500');

-- ===========================================
-- VENUE ID UPDATES
-- ===========================================

-- Row 2: ARAI BAYSIDE -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI BAYSIDE' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 2
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI BAYSIDE' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 3: ARAI BAYSIDE -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI BAYSIDE' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 3
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI BAYSIDE' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 4: ARAI BAYSIDE (DAI 2 YARD) -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI BAYSIDE (DAI 2 YARD)' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 4
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI BAYSIDE (DAI 2 YARD)' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 5: ARAI OYAMA -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 5
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 6: ARAI OYAMA -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 6
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 7: ARAI OYAMA -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'HIACE' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%HIACE%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%HIACE%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'HIACE')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 7
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 8: ARAI OYAMA -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCK' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCK%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCK%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCK')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 8
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI OYAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 9: ARAI SENDAI -> venue_id: 22431
UPDATE rixo_prices 
SET venue_id = '22431'
WHERE UPPER(TRIM(auction_name)) = 'ARAI SENDAI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 10: AUCNETVAA (KISARAZU) -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'AUCNETVAA (KISARAZU)' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 10
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'AUCNETVAA (KISARAZU)' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 11: AUCNETVAA (SAKURA) -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'AUCNETVAA (SAKURA)' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 11
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'AUCNETVAA (SAKURA)' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 12: BAYAUC -> venue_id: 24016
UPDATE rixo_prices 
SET venue_id = '24016'
WHERE UPPER(TRIM(auction_name)) = 'BAYAUC' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR / BIG CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR / BIG CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 12
UPDATE rixo_prices 
SET venue_id = '24016'
WHERE UPPER(TRIM(auction_name)) = 'BAYAUC' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 13: CAA CHUBU -> venue_id: T008288
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA CHUBU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR / BIG CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR / BIG CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 13
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA CHUBU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 14: CAA GIFU -> venue_id: T008288
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA GIFU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR / BIG CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR / BIG CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 14
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA GIFU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 15: CAA TOHOKU -> venue_id: T008288
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOHOKU' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR / BIG CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR / BIG CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 15
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOHOKU' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 16: CAA TOKYO -> venue_id: T008288
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR / BIG CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR / BIG CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 16
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 17: CAA TOKYO -> venue_id: T008288
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR / BIG CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR / BIG CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 17
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 18: CAA TOKYO -> venue_id: T008288
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCK' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCK%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCK%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCK')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 18
UPDATE rixo_prices 
SET venue_id = 'T008288'
WHERE UPPER(TRIM(auction_name)) = 'CAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 19: HAA KOBE -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'HAA KOBE' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 20: HAA KOBE -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'HAA KOBE' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'ECL KOBE'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 21: HERO -> venue_id: 30617
UPDATE rixo_prices 
SET venue_id = '30617'
WHERE UPPER(TRIM(auction_name)) = 'HERO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 22: HONDA HOKKAIDO -> venue_id: 1355400
UPDATE rixo_prices 
SET venue_id = '1355400'
WHERE UPPER(TRIM(auction_name)) = 'HONDA HOKKAIDO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 23: HONDA KANSAI -> venue_id: 1355400
UPDATE rixo_prices 
SET venue_id = '1355400'
WHERE UPPER(TRIM(auction_name)) = 'HONDA KANSAI' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 24: HONDA KYUSHU -> venue_id: 1355400
UPDATE rixo_prices 
SET venue_id = '1355400'
WHERE UPPER(TRIM(auction_name)) = 'HONDA KYUSHU' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 25: HONDA NAGOYA -> venue_id: 1355400
UPDATE rixo_prices 
SET venue_id = '1355400'
WHERE UPPER(TRIM(auction_name)) = 'HONDA NAGOYA' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 26: HONDA SENDAI -> venue_id: 1355400
UPDATE rixo_prices 
SET venue_id = '1355400'
WHERE UPPER(TRIM(auction_name)) = 'HONDA SENDAI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 27: HONDA TOKYO -> venue_id: 1355400
UPDATE rixo_prices 
SET venue_id = '1355400'
WHERE UPPER(TRIM(auction_name)) = 'HONDA TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 28: IAA OSAKA -> venue_id: 27791
UPDATE rixo_prices 
SET venue_id = '27791'
WHERE UPPER(TRIM(auction_name)) = 'IAA OSAKA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 29: ISUZU KOBE -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'ISUZU KOBE' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 30: ISUZU KOBE (SAKURAI YARD) -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'ISUZU KOBE (SAKURAI YARD)' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 31: ISUZU KYUSHU -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'ISUZU KYUSHU' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 32: ISUZU TOKYO -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'ISUZU TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 33: JAA -> venue_id: 11390
UPDATE rixo_prices 
SET venue_id = '11390'
WHERE UPPER(TRIM(auction_name)) = 'JAA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 34: JAA -> venue_id: 11390
UPDATE rixo_prices 
SET venue_id = '11390'
WHERE UPPER(TRIM(auction_name)) = 'JAA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 35: JU AICHI -> venue_id: 95518
UPDATE rixo_prices 
SET venue_id = '95518'
WHERE UPPER(TRIM(auction_name)) = 'JU AICHI' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 36: JU AOMORI -> venue_id: 200539
UPDATE rixo_prices 
SET venue_id = '200539'
WHERE UPPER(TRIM(auction_name)) = 'JU AOMORI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 37: JU CHIBA -> venue_id: 59077
UPDATE rixo_prices 
SET venue_id = '59077'
WHERE UPPER(TRIM(auction_name)) = 'JU CHIBA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 38: JU CHIBA -> venue_id: 59077
UPDATE rixo_prices 
SET venue_id = '59077'
WHERE UPPER(TRIM(auction_name)) = 'JU CHIBA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 39: JU FUKUI -> venue_id: 126589
UPDATE rixo_prices 
SET venue_id = '126589'
WHERE UPPER(TRIM(auction_name)) = 'JU FUKUI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 40: JU FUKUOKA -> venue_id: 37488
UPDATE rixo_prices 
SET venue_id = '37488'
WHERE UPPER(TRIM(auction_name)) = 'JU FUKUOKA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 41: JU FUKUSHIMA -> venue_id: 88472
UPDATE rixo_prices 
SET venue_id = '88472'
WHERE UPPER(TRIM(auction_name)) = 'JU FUKUSHIMA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 42: JU GIFU -> venue_id: 50354
UPDATE rixo_prices 
SET venue_id = '50354'
WHERE UPPER(TRIM(auction_name)) = 'JU GIFU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 43: JU GUNMA -> venue_id: 700485
UPDATE rixo_prices 
SET venue_id = '700485'
WHERE UPPER(TRIM(auction_name)) = 'JU GUNMA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 44: JU HIROSHIMA -> venue_id: 77510
UPDATE rixo_prices 
SET venue_id = '77510'
WHERE UPPER(TRIM(auction_name)) = 'JU HIROSHIMA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 45: JU HOKKAIDO -> venue_id: 63257
UPDATE rixo_prices 
SET venue_id = '63257'
WHERE UPPER(TRIM(auction_name)) = 'JU HOKKAIDO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 46: JU IBARAKI -> venue_id: 80548
UPDATE rixo_prices 
SET venue_id = '80548'
WHERE UPPER(TRIM(auction_name)) = 'JU IBARAKI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 47: JU ISHIKAWA -> venue_id: 70496
UPDATE rixo_prices 
SET venue_id = '70496'
WHERE UPPER(TRIM(auction_name)) = 'JU ISHIKAWA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 48: JU KANAGAWA -> venue_id: 90471
UPDATE rixo_prices 
SET venue_id = '90471'
WHERE UPPER(TRIM(auction_name)) = 'JU KANAGAWA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 49: JU KANAGAWA -> venue_id: 90471
UPDATE rixo_prices 
SET venue_id = '90471'
WHERE UPPER(TRIM(auction_name)) = 'JU KANAGAWA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 50: JU KUMAMOTO -> venue_id: 70513
UPDATE rixo_prices 
SET venue_id = '70513'
WHERE UPPER(TRIM(auction_name)) = 'JU KUMAMOTO' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 51: JU MIE -> venue_id: 316009
UPDATE rixo_prices 
SET venue_id = '316009'
WHERE UPPER(TRIM(auction_name)) = 'JU MIE' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 52: JU MIYAGI -> venue_id: 70506
UPDATE rixo_prices 
SET venue_id = '70506'
WHERE UPPER(TRIM(auction_name)) = 'JU MIYAGI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 53: JU MIYAZAKI -> venue_id: 70519
UPDATE rixo_prices 
SET venue_id = '70519'
WHERE UPPER(TRIM(auction_name)) = 'JU MIYAZAKI' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 54: JU NAGANO -> venue_id: 80536
UPDATE rixo_prices 
SET venue_id = '80536'
WHERE UPPER(TRIM(auction_name)) = 'JU NAGANO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 55: JU NAGASAKI -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'JU NAGASAKI' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 56: JU NARA -> venue_id: 130528
UPDATE rixo_prices 
SET venue_id = '130528'
WHERE UPPER(TRIM(auction_name)) = 'JU NARA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 57: JU NIIGATA -> venue_id: 45548
UPDATE rixo_prices 
SET venue_id = '45548'
WHERE UPPER(TRIM(auction_name)) = 'JU NIIGATA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 58: JU OITA -> venue_id: 45522
UPDATE rixo_prices 
SET venue_id = '45522'
WHERE UPPER(TRIM(auction_name)) = 'JU OITA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 59: JU OKINAWA -> venue_id: 53143
UPDATE rixo_prices 
SET venue_id = '53143'
WHERE UPPER(TRIM(auction_name)) = 'JU OKINAWA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 60: JU SAITAMA -> venue_id: 16845
UPDATE rixo_prices 
SET venue_id = '16845'
WHERE UPPER(TRIM(auction_name)) = 'JU SAITAMA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 61: JU SAITAMA -> venue_id: 16845
UPDATE rixo_prices 
SET venue_id = '16845'
WHERE UPPER(TRIM(auction_name)) = 'JU SAITAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 62: JU SHIMANE -> venue_id: A052166
UPDATE rixo_prices 
SET venue_id = 'A052166'
WHERE UPPER(TRIM(auction_name)) = 'JU SHIMANE'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 63: JU SHIZUOKA -> venue_id: 900513
UPDATE rixo_prices 
SET venue_id = '900513'
WHERE UPPER(TRIM(auction_name)) = 'JU SHIZUOKA' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 64: JU TOCHIGI -> venue_id: 80521
UPDATE rixo_prices 
SET venue_id = '80521'
WHERE UPPER(TRIM(auction_name)) = 'JU TOCHIGI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 65: JU TOKYO -> venue_id: 20558
UPDATE rixo_prices 
SET venue_id = '20558'
WHERE UPPER(TRIM(auction_name)) = 'JU TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 66: JU TOYAMA -> venue_id: 600525
UPDATE rixo_prices 
SET venue_id = '600525'
WHERE UPPER(TRIM(auction_name)) = 'JU TOYAMA' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 67: JU TOYAMA -> venue_id: 600525
UPDATE rixo_prices 
SET venue_id = '600525'
WHERE UPPER(TRIM(auction_name)) = 'JU TOYAMA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 68: JU YAMAGATA -> venue_id: 90532
UPDATE rixo_prices 
SET venue_id = '90532'
WHERE UPPER(TRIM(auction_name)) = 'JU YAMAGATA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 69: JU YAMAGUCHI -> venue_id: 59160
UPDATE rixo_prices 
SET venue_id = '59160'
WHERE UPPER(TRIM(auction_name)) = 'JU YAMAGUCHI' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 70: JU YAMANASHI -> venue_id: 300541
UPDATE rixo_prices 
SET venue_id = '300541'
WHERE UPPER(TRIM(auction_name)) = 'JU YAMANASHI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 71: KCAA FUKUOKA -> venue_id: J2671
UPDATE rixo_prices 
SET venue_id = 'J2671'
WHERE UPPER(TRIM(auction_name)) = 'KCAA FUKUOKA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 72: KCAA KYOTO -> venue_id: J2671
UPDATE rixo_prices 
SET venue_id = 'J2671'
WHERE UPPER(TRIM(auction_name)) = 'KCAA KYOTO' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 73: KCAA MINAMI KYUSHU -> venue_id: J2671
UPDATE rixo_prices 
SET venue_id = 'J2671'
WHERE UPPER(TRIM(auction_name)) = 'KCAA MINAMI KYUSHU' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 74: KCAA YAMAGUCHI -> venue_id: J2671
UPDATE rixo_prices 
SET venue_id = 'J2671'
WHERE UPPER(TRIM(auction_name)) = 'KCAA YAMAGUCHI' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 75: LAA OKAYAMA -> venue_id: 00S7784
UPDATE rixo_prices 
SET venue_id = '00S7784'
WHERE UPPER(TRIM(auction_name)) = 'LAA OKAYAMA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 76: LAA SHIKOKU -> venue_id: 00S7784
UPDATE rixo_prices 
SET venue_id = '00S7784'
WHERE UPPER(TRIM(auction_name)) = 'LAA SHIKOKU' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 77: LUM FUKUOKA -> venue_id: 1564
UPDATE rixo_prices 
SET venue_id = '1564'
WHERE UPPER(TRIM(auction_name)) = 'LUM FUKUOKA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 78: LUM HOKKAIDO -> venue_id: 1564
UPDATE rixo_prices 
SET venue_id = '1564'
WHERE UPPER(TRIM(auction_name)) = 'LUM HOKKAIDO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 79: LUM KOBE -> venue_id: 1564
UPDATE rixo_prices 
SET venue_id = '1564'
WHERE UPPER(TRIM(auction_name)) = 'LUM KOBE' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 80: LUM KOBE (HIROSHIMA) -> venue_id: 1564
UPDATE rixo_prices 
SET venue_id = '1564'
WHERE UPPER(TRIM(auction_name)) = 'LUM KOBE (HIROSHIMA)' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 81: LUM NAGOYA -> venue_id: 1564
UPDATE rixo_prices 
SET venue_id = '1564'
WHERE UPPER(TRIM(auction_name)) = 'LUM NAGOYA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 82: LUM TOKYO -> venue_id: 1564
UPDATE rixo_prices 
SET venue_id = '1564'
WHERE UPPER(TRIM(auction_name)) = 'LUM TOKYO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 83: LUM TOKYO (SENDAI) -> venue_id: 1564
UPDATE rixo_prices 
SET venue_id = '1564'
WHERE UPPER(TRIM(auction_name)) = 'LUM TOKYO (SENDAI)' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 84: MIRIVE AICHI -> venue_id: 710596
UPDATE rixo_prices 
SET venue_id = '710596'
WHERE UPPER(TRIM(auction_name)) = 'MIRIVE AICHI' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 85: MIRIVE OSAKA -> venue_id: 710596
UPDATE rixo_prices 
SET venue_id = '710596'
WHERE UPPER(TRIM(auction_name)) = 'MIRIVE OSAKA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 86: MIRIVE SAITAMA -> venue_id: 710596
UPDATE rixo_prices 
SET venue_id = '710596'
WHERE UPPER(TRIM(auction_name)) = 'MIRIVE SAITAMA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 87: MIRIVE SAITAMA -> venue_id: 710596
UPDATE rixo_prices 
SET venue_id = '710596'
WHERE UPPER(TRIM(auction_name)) = 'MIRIVE SAITAMA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'AQUA LOGISTICS'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 88: NAA FUKUOKA -> venue_id: 9733100
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA FUKUOKA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 89: NAA NAGOYA -> venue_id: 9733100
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA NAGOYA' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 90: NAA NAGOYA (HOKURIKU) -> venue_id: 9733100
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA NAGOYA (HOKURIKU)' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 91: NAA NAGOYA (HOKURIKU) -> venue_id: 9733100
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA NAGOYA (HOKURIKU)' AND UPPER(TRIM(rixo_company)) = 'HIDA' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 92: NAA OSAKA -> venue_id: 9733100
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA OSAKA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 93: NAA TOKYO -> venue_id: 9733100
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 93
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 94: NAA TOKYO -> venue_id: 9733100
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 94
UPDATE rixo_prices 
SET venue_id = '9733100'
WHERE UPPER(TRIM(auction_name)) = 'NAA TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 95: NOAA -> venue_id: Z289700
UPDATE rixo_prices 
SET venue_id = 'Z289700'
WHERE UPPER(TRIM(auction_name)) = 'NOAA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 96: NPS FUKUOKA -> venue_id: 7378
UPDATE rixo_prices 
SET venue_id = '7378'
WHERE UPPER(TRIM(auction_name)) = 'NPS FUKUOKA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 97: NPS OSAKA -> venue_id: 7378
UPDATE rixo_prices 
SET venue_id = '7378'
WHERE UPPER(TRIM(auction_name)) = 'NPS OSAKA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 98: NPS SENDAI -> venue_id: 7378
UPDATE rixo_prices 
SET venue_id = '7378'
WHERE UPPER(TRIM(auction_name)) = 'NPS SENDAI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 99: NPS TOCHIGI -> venue_id: 7378
UPDATE rixo_prices 
SET venue_id = '7378'
WHERE UPPER(TRIM(auction_name)) = 'NPS TOCHIGI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 100: NPS TOKYO -> venue_id: 7378
UPDATE rixo_prices 
SET venue_id = '7378'
WHERE UPPER(TRIM(auction_name)) = 'NPS TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 101: NPS TOMAKOMAI -> venue_id: 7378
UPDATE rixo_prices 
SET venue_id = '7378'
WHERE UPPER(TRIM(auction_name)) = 'NPS TOMAKOMAI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 102: ORIX ATSUGI -> venue_id: 50000052
UPDATE rixo_prices 
SET venue_id = '50000052'
WHERE UPPER(TRIM(auction_name)) = 'ORIX ATSUGI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 103: ORIX ATSUGI (OYAMA) -> venue_id: 50000052
UPDATE rixo_prices 
SET venue_id = '50000052'
WHERE UPPER(TRIM(auction_name)) = 'ORIX ATSUGI (OYAMA)' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 104: ORIX FUKUOKA -> venue_id: 50000052
UPDATE rixo_prices 
SET venue_id = '50000052'
WHERE UPPER(TRIM(auction_name)) = 'ORIX FUKUOKA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 105: ORIX KOBE -> venue_id: 50000052
UPDATE rixo_prices 
SET venue_id = '50000052'
WHERE UPPER(TRIM(auction_name)) = 'ORIX KOBE' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 106: ORIX SENDAI -> venue_id: 50000052
UPDATE rixo_prices 
SET venue_id = '50000052'
WHERE UPPER(TRIM(auction_name)) = 'ORIX SENDAI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 107: SAA HAMAMATSU -> venue_id: 3732
UPDATE rixo_prices 
SET venue_id = '3732'
WHERE UPPER(TRIM(auction_name)) = 'SAA HAMAMATSU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 108: SAA SAPPORO -> venue_id: 57455
UPDATE rixo_prices 
SET venue_id = '57455'
WHERE UPPER(TRIM(auction_name)) = 'SAA SAPPORO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 109: TAA CHUBU -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA CHUBU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 110: TAA CHUBU (HOKURIKU) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA CHUBU (HOKURIKU)' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 111: TAA CHUBU (HOKURIKU) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA CHUBU (HOKURIKU)' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 112: TAA CHUBU (SHIZUOKA) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA CHUBU (SHIZUOKA)' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 113: TAA HIROSHIMA -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA HIROSHIMA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 114: TAA HOKKAIDO -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA HOKKAIDO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 115: TAA HYOGO -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA HYOGO' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 116: TAA KANTO -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 117: TAA KANTO -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 117
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 118: TAA KANTO -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'BIG CAR / TRUCK' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%BIG CAR / TRUCK%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%BIG CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'BIG CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 118
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 119: TAA KANTO (KITA KANTO) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO (KITA KANTO)' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 120: TAA KANTO (SAITAMA) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO (SAITAMA)' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 121: TAA KANTO (SAITAMA) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO (SAITAMA)' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 122: TAA KANTO (TAMA) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KANTO (TAMA)' AND UPPER(TRIM(rixo_company)) = 'TAA' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 123: TAA KINKI -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KINKI' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 124: TAA KINKI (SHIGA YARD) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KINKI (SHIGA YARD)' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 125: TAA KYUSHU -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA KYUSHU' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 126: TAA MINAMI KYUSHU -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA MINAMI KYUSHU' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 127: TAA SHIKOKU -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA SHIKOKU' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 128: TAA SHIKOKU (EHIME) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA SHIKOKU (EHIME)' AND UPPER(TRIM(rixo_company)) = 'TAA' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 129: TAA TOHOKU -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA TOHOKU' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 130: TAA TOHOKU (MIYAGI) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA TOHOKU (MIYAGI)' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 131: TAA YOKOHAMA -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA YOKOHAMA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 132: TAA YOKOHAMA -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA YOKOHAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 133: TAA YOKOHAMA (ATSUGI) -> venue_id: 65010
UPDATE rixo_prices 
SET venue_id = '65010'
WHERE UPPER(TRIM(auction_name)) = 'TAA YOKOHAMA (ATSUGI)' AND UPPER(TRIM(rixo_company)) = 'TAA' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 134: USS FUKUOKA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS FUKUOKA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 135: USS GUNMA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS GUNMA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 136: USS HOKURIKU -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS HOKURIKU' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 137: USS HOKURIKU -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS HOKURIKU' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 138: USS KOBE -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS KOBE' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 139: USS KYUSHU -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS KYUSHU' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 140: USS NAGOYA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS NAGOYA' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 141: USS NIIGATA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS NIIGATA' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 142: USS OKAYAMA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS OKAYAMA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 143: USS OSAKA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS OSAKA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 144: USS R-NAGOYA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS R-NAGOYA' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 145: USS SAITAMA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS SAITAMA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 146: USS SAPPORO -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS SAPPORO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 147: USS SHIZUOKA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS SHIZUOKA' AND UPPER(TRIM(rixo_company)) = 'STYLISH AUTO' AND UPPER(TRIM(stock_location)) = 'GLOBAL NAGOYA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 148: USS TOHOKU -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOHOKU' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 149: USS TOKYO -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 149
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 150: USS TOKYO -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%CAR%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'CAR')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 150
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 151: USS TOKYO -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'G CLASS / LAND CRUISER / ' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%G CLASS / LAND CRUISER / %' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%G CLASS%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'G CLASS')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 151
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 152: USS TOKYO -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCKS' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCKS%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCKS%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCKS')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 152
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 153: USS YOKOHAMA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS YOKOHAMA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 154: USS YOKOHAMA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS YOKOHAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 155: USS YOKOHAMA -> venue_id: E0483
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS YOKOHAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI' AND (UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCKS BUS' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCKS BUS%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%TRUCKS BUS%' OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = 'TRUCKS BUS')
  AND (venue_id IS NULL OR venue_id = '');
-- Fallback (without type_of_vehicle constraint) for Row 155
UPDATE rixo_prices 
SET venue_id = 'E0483'
WHERE UPPER(TRIM(auction_name)) = 'USS YOKOHAMA' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 156: ZERO CHIBA -> venue_id: B2B901
UPDATE rixo_prices 
SET venue_id = 'B2B901'
WHERE UPPER(TRIM(auction_name)) = 'ZERO CHIBA' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 157: ZERO HAKATA -> venue_id: B2B901
UPDATE rixo_prices 
SET venue_id = 'B2B901'
WHERE UPPER(TRIM(auction_name)) = 'ZERO HAKATA' AND UPPER(TRIM(rixo_company)) = 'Y''S' AND UPPER(TRIM(stock_location)) = 'GLOBAL HAKATA'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 158: ZERO HOKKAIDO -> venue_id: B2B901
UPDATE rixo_prices 
SET venue_id = 'B2B901'
WHERE UPPER(TRIM(auction_name)) = 'ZERO HOKKAIDO' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 159: ZERO OSAKA -> venue_id: B2B901
UPDATE rixo_prices 
SET venue_id = 'B2B901'
WHERE UPPER(TRIM(auction_name)) = 'ZERO OSAKA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 160: ZERO SENDAI -> venue_id: B2B901
UPDATE rixo_prices 
SET venue_id = 'B2B901'
WHERE UPPER(TRIM(auction_name)) = 'ZERO SENDAI' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 161: ZERO SHONAN -> venue_id: B2B901
UPDATE rixo_prices 
SET venue_id = 'B2B901'
WHERE UPPER(TRIM(auction_name)) = 'ZERO SHONAN' AND UPPER(TRIM(rixo_company)) = 'LOGICO' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 162: ZIP OSAKA -> venue_id: 41452
UPDATE rixo_prices 
SET venue_id = '41452'
WHERE UPPER(TRIM(auction_name)) = 'ZIP OSAKA' AND UPPER(TRIM(rixo_company)) = 'KLC' AND UPPER(TRIM(stock_location)) = 'KLC'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 163: ZIP TOKYO -> venue_id: 41452
UPDATE rixo_prices 
SET venue_id = '41452'
WHERE UPPER(TRIM(auction_name)) = 'ZIP TOKYO' AND UPPER(TRIM(rixo_company)) = 'YAMAZAKI' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');

-- Row 164: ZIP TOKYO -> venue_id: 41452
UPDATE rixo_prices 
SET venue_id = '41452'
WHERE UPPER(TRIM(auction_name)) = 'ZIP TOKYO' AND UPPER(TRIM(rixo_company)) = 'SHAHBAZ' AND UPPER(TRIM(stock_location)) = 'GLOBAL KAWASAKI'
  AND (venue_id IS NULL OR venue_id = '');



-- ===========================================
-- CURRENCY SYMBOL CLEANUP
-- ===========================================

-- Remove currency symbols and commas from rixo_price column
-- This migration cleans existing data to store only numeric values
-- Currency formatting will be handled on the frontend
UPDATE rixo_prices 
SET rixo_price = REGEXP_REPLACE(rixo_price, '[^0-9]', '')
WHERE (rixo_price LIKE '%¥%' 
    OR rixo_price LIKE '%Â%'
    OR rixo_price LIKE '%,%'
    OR rixo_price REGEXP '[^0-9]')
    AND rixo_price IS NOT NULL 
    AND rixo_price != '';

