-- Per-chassis stock location on shipping history (from booking / purchase).
ALTER TABLE shipping_history
    ADD COLUMN stock_location VARCHAR(255) NULL AFTER pol;
