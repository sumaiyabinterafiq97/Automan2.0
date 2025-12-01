-- Add vessel column to purchases table
ALTER TABLE purchases ADD COLUMN vessel VARCHAR(255) DEFAULT NULL;

