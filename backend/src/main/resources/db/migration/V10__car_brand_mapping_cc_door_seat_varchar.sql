-- Store multiple semicolon-separated values for CC, door, and seat (same pattern as wd/shift).
ALTER TABLE car_brand_mapping MODIFY cc VARCHAR(100) NULL;
ALTER TABLE car_brand_mapping MODIFY door VARCHAR(100) NULL;
ALTER TABLE car_brand_mapping MODIFY seat VARCHAR(100) NULL;
