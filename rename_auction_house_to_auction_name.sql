-- Rename auction_house column to auction_name in rixo_prices table
ALTER TABLE rixo_prices CHANGE COLUMN auction_house auction_name VARCHAR(255) NOT NULL;
