-- Make CC column nullable (only chassis should be required)
ALTER TABLE purchases MODIFY COLUMN CC INT NULL;

