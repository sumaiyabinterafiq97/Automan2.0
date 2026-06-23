-- Add car_model_year column to car_brand_mapping table
-- This stores the production date(s) for which recycle fees are defined
-- Stored as semicolon-delimited YYYY-MM values (e.g. "2019-01;2020-05;2021-03")
-- and kept in sync with the recycle_fee column (e.g. "2019-01:10000;2020-05:12490")
ALTER TABLE car_brand_mapping
    ADD COLUMN car_model_year TEXT NULL AFTER recycle_fee;
