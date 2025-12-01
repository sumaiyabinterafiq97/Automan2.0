-- Car Brand Mapping Table Migration
-- Create table for car brand, chassis, car name, fuel, WD, shift, CC, door, and grade mappings

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
    grade VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_car_brand (car_brand),
    INDEX idx_chassis (chassis),
    INDEX idx_car_name (car_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert data from CSV

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZN6',
    '86',
    'GASOLINE',
    '2WD',
    NULL,
    2000,
    2,
    'G'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP30',
    'bB',
    'GASOLINE',
    '2WD',
    NULL,
    1300,
    5,
    'S'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP31',
    'bB',
    'GASOLINE',
    '2WD',
    NULL,
    1500,
    5,
    'Z'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP34',
    'bB',
    'GASOLINE',
    '2WD',
    NULL,
    1500,
    2,
    'OPEN DECK'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP35',
    'bB',
    'GASOLINE',
    '4WD',
    NULL,
    1500,
    5,
    'Z'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'QNC20',
    'bB',
    'GASOLINE',
    '2WD',
    NULL,
    1300,
    5,
    'S X VER'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'QNC21',
    'bB',
    'GASOLINE',
    '2WD',
    NULL,
    1300,
    5,
    'S KIRAMEKI'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'QNC25',
    'bB',
    'GASOLINE',
    '4WD',
    NULL,
    1300,
    5,
    'S'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NGX10',
    'C-HR',
    'GASOLINE',
    '2WD',
    NULL,
    1200,
    5,
    'S-T'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NGX50',
    'C-HR',
    'GASOLINE',
    '4WD',
    NULL,
    1200,
    5,
    'S-T'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZYX10',
    'C-HR',
    'HYBRID',
    '2WD',
    NULL,
    1800,
    5,
    'S'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZYX11',
    'C-HR',
    'HYBRID',
    '2WD',
    NULL,
    1800,
    5,
    'S'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACA21',
    'RAV4',
    'GASOLINE',
    '4WD',
    NULL,
    2000,
    5,
    'X'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACA31',
    'RAV4',
    'GASOLINE',
    '4WD',
    NULL,
    2400,
    5,
    'X'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACA36',
    'RAV4',
    'GASOLINE',
    '2WD',
    NULL,
    2400,
    5,
    'X'
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AXAH52',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AXAH54',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MXAA52',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MXAA54',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SXA10',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SXA11',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SXA15',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZCA26',
    'RAV4',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANM10',
    'ISIS',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANM15',
    'ISIS',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZGM10',
    'ISIS',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZGM11',
    'ISIS',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZGM15',
    'ISIS',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZNM10',
    'ISIS',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MXPK10',
    'AQUA',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MXPK11',
    'AQUA',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MXPK16',
    'AQUA',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NHP10',
    'AQUA',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AZT240',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZT240',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZT260',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRT260',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRT261',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRT265',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZT240',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZT245',
    'ALLION',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH10',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH15',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MNH10',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MNH15',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AGH30',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AGH35',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AGH40',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AGH45',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH10',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH15',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH20',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH25',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH20',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH25',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH30',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH35',
    'ALPHARD',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AAHP45',
    'ALPHARD PHEV',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AAHH40',
    'ALPHARD HV',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AAHH45',
    'ALPHARD HV',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ATH10',
    'ALPHARD HV',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ATH20',
    'ALPHARD HV',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AYH30',
    'ALPHARD HV',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE121',
    'ALLEX',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE124',
    'ALLEX',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZE122',
    'ALLEX',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZE123',
    'ALLEX',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZE124',
    'ALLEX',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP110',
    'IST',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP115',
    'IST',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP60',
    'IST',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP61',
    'IST',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP65',
    'IST',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZSP110',
    'IST',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACM21',
    'IPSUM',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACM26',
    'IPSUM',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SXM10',
    'IPSUM',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SXM15',
    'IPSUM',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANE10',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANE11',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZGE20',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZGE21',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZGE22',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZGE25',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZNE10',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZNE14',
    'WISH',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'KSP130',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'KSP90',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP10',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP13',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NC131',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP15',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP91',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NCP95',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NHP130',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NSP130',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NSP131',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NSP135',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SCP10',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SCP13',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SCP90',
    'VITZ',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AGH30',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AGH35',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH20',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ANH25',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH20',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH25',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH30',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GGH35',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TAHA40',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TAHA45',
    'VELLFIRE',
    'GASOLINE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AAHP45W',
    'VELLFIRE',
    'PHEV',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AAHH40',
    'VELLFIRE',
    'HYBRID',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AAHH45',
    'VELLFIRE',
    'HYBRID',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ATH20',
    'VELLFIRE',
    'HYBRID',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AYH30',
    'VELLFIRE',
    'HYBRID',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AZR60',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AZR65',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MZRA90',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MZRA92',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MZRA95',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRR70',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRR75',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRR80',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRR85',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWR80',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWR90',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWR92',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWR95',
    'VOXY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRR80',
    'ESQUIRE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRR85',
    'ESQUIRE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWR80',
    'ESQUIRE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACR30',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACR40',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACR50',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACR55',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GSR50',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'GSR55',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MCR30',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MCR40',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR10',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR11',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR20',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR21',
    'ESTIMA',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'CXR10',
    'Toyota Estima Emina',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'CXR20',
    'Toyota Estima Emina',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR10',
    'Toyota Estima Emina',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR20',
    'Toyota Estima Emina',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR21',
    'Toyota Estima Emina',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR10',
    'Toyota Estima Lucida',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR11',
    'Toyota Estima Lucida',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR20',
    'Toyota Estima Lucida',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'TCR21',
    'Toyota Estima Lucida',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AHR10',
    'Toyota Estima Hybrid',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AHR20',
    'Toyota Estima Hybrid',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NRE185',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE151',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE154',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE181',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE184',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRE152',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRE154',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRE186',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWE186',
    'Toyota Auris',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACV30',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACV35',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACV40',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ACV45',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AVV50',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AXVH70',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AXVH75',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SV22',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SV30',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SV32',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SV40',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SV41',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SXV20',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'SXV25',
    'Toyota Camry',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE100',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE101',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE110',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE111',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE114',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE91',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE92',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'CE100',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'CE104',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'CE110',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'CE113',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'CE114',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'EE111',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'KE10',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'KE11',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'KE15',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'KE20',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MZEA17',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NRE210',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE120',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE121',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE124',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRE212',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWE211',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWE214',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWE215',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZWE219',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZE122',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZE124',
    'Toyota Corolla',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NKE165',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NRE160',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NRE161',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE141',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE144',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE161',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE164',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRE142',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZRE144',
    'Toyota Corolla Axio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MXGA10',
    'Toyota Corolla Cross',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'MXGH15',
    'Toyota Corolla Cross',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZSG10',
    'Toyota Corolla Cross',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZVG11',
    'Toyota Corolla Cross',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZVG13',
    'Toyota Corolla Cross',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZVG15',
    'Toyota Corolla Cross',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZVG16',
    'Toyota Corolla Cross',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE111',
    'Toyota Corolla Spacio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'AE115',
    'Toyota Corolla Spacio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'NZE121',
    'Toyota Corolla Spacio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZE122',
    'Toyota Corolla Spacio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

INSERT INTO car_brand_mapping (car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade) VALUES (
    'TOYOTA',
    'ZZE124',
    'Toyota Corolla Spacio',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

-- Total rows inserted: 228
