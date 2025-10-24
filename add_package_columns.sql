-- Add package price and package mode columns to purchases table
ALTER TABLE purchases ADD COLUMN package_price VARCHAR(255) DEFAULT NULL;
ALTER TABLE purchases ADD COLUMN is_package_mode BOOLEAN DEFAULT FALSE;

-- Update existing records to have default values
UPDATE purchases SET is_package_mode = FALSE WHERE is_package_mode IS NULL;
