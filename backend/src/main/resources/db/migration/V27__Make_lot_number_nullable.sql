-- Make lot_number nullable (only chassis should be required)
ALTER TABLE purchases MODIFY COLUMN lot_number VARCHAR(50) NULL;

