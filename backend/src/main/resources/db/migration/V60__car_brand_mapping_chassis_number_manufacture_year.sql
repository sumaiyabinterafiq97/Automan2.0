-- Chassis number + manufacture year pairs per chassis code (e.g. "67H:2019;t6yg:2020")
ALTER TABLE car_brand_mapping
    ADD COLUMN chassis_number TEXT NULL,
    ADD COLUMN manufacture_year TEXT NULL;
