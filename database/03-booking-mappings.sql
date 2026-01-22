-- Booking mappings table creation and seed data
USE automan_car_purchase;

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

-- Seed booking_mappings table with country defaults and client-specific overrides
-- This script is non-destructive and uses INSERT IGNORE to prevent duplicates

-- Country-level defaults (POD and Consignee)

-- Pakistan default
INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES (
    'PAKISTAN',
    'KARACHI',
    'OVERSEAS TRANSIT AGENCY (PVT) LTD.',
    '1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI'
);

-- Kenya default
INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES (
    'KENYA',
    'MOMBASA',
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- South Africa default
INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES (
    'SOUTH AFRICA',
    'DURBAN',
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- Mozambique default
INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES (
    'MOZAMBIQUE',
    'MAPUTO',
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- Uganda default
INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES (
    'UGANDA',
    NULL,
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- UAE default
INSERT IGNORE INTO booking_mappings (country, pod, consignee_name, consignee_address)
VALUES (
    'UAE',
    'JABEL ALI-DUBAI',
    'LAKHANI MOTORS FZE',
    'SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com'
);

-- Client-specific overrides

-- SHEHROZE MOTORS -> KARACHI-PAKISTAN
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'PAKISTAN',
    'SHEHROZE MOTORS',
    'KARACHI',
    'GLOBAL KAWASAKI',
    'YOKOHAMA',
    'OVERSEAS TRANSIT AGENCY (PVT) LTD.',
    '1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI'
);

-- DAAVI AUTO -> MOMBASA-KENYA
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'KENYA',
    'DAAVI AUTO',
    'MOMBASA',
    'AQUA LOGISTICS',
    'YOKOHAMA',
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- NEW GRAND AUTO (JAWAD) -> UGANDA
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'UGANDA',
    'NEW GRAND AUTO (JAWAD)',
    NULL,
    'GLOBAL NAGOYA',
    'NAGOYA',
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- IRSHAD ALI AKHTAR -> MAPUTO-MOZAMBIQUE
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'MOZAMBIQUE',
    'IRSHAD ALI AKHTAR',
    'MAPUTO',
    'FLASHRISE',
    'NAGOYA',
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- AAMIR DEDHI -> JABEL ALI-DUBAI
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'UAE',
    'AAMIR DEDHI',
    'JABEL ALI-DUBAI',
    'KLC',
    'OSAKA,SENBOKU,KOBE',
    'LAKHANI MOTORS FZE',
    'SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com'
);

-- AUTOHANDLER -> DURBAN-SOUTH AFRICA
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'SOUTH AFRICA',
    'AUTOHANDLER',
    'DURBAN',
    'GLOBAL HAKATA',
    'HAKATA',
    'LAKHANI MOTORS (K) LTD',
    'P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM'
);

-- ESSA ADMANI -> UK
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'UK',
    'ESSA ADMANI',
    NULL,
    'BARAKI PARKING',
    NULL,
    NULL,
    NULL
);

-- IRFAN MEMON HYDERABAD -> NEWZEALAND
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'NEWZEALAND',
    'IRFAN MEMON HYDERABAD',
    NULL,
    'LOCAL',
    NULL,
    NULL,
    NULL
);

-- NAVEES AHMAD -> LOCAL-JAPAN
INSERT IGNORE INTO booking_mappings (country, client_name, pod, stock_location, pols, consignee_name, consignee_address)
VALUES (
    'JAPAN',
    'NAVEES AHMAD',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

