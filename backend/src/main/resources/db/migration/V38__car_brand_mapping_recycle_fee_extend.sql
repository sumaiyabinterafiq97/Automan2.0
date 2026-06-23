-- Extend recycle_fee column to TEXT to support multiple MM/YYYY:fee pairs
-- Format: "YYYY-MM:fee;YYYY-MM:fee" (semicolon-delimited)
-- e.g. "2019-01:10000;2020-05:12490;2021-03:13000"
ALTER TABLE car_brand_mapping
    MODIFY COLUMN recycle_fee TEXT NULL;
