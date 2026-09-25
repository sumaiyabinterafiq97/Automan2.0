-- POL for purchases/shipping lives on stock_location_map (and saved purchase/history rows).
-- Supplier Map / Rixo Price Map no longer store POL on rixo_mapping.
ALTER TABLE rixo_mapping DROP COLUMN pol;
