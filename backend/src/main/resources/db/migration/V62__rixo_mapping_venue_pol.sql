-- Extend rixo_mapping with supplier-map fields from rixo_prices (venue, POL).
ALTER TABLE rixo_mapping
    ADD COLUMN venue_id VARCHAR(255) NULL AFTER stock_location,
    ADD COLUMN pol VARCHAR(255) NULL AFTER venue_id;
