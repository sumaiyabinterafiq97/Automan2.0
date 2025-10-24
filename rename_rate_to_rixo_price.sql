-- Migration script to rename 'rate' column to 'rixo_price' in rixo_prices table
-- This script should be run on existing databases that have the 'rate' column

ALTER TABLE rixo_prices CHANGE COLUMN rate rixo_price VARCHAR(255);
