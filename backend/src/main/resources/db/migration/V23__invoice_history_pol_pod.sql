-- POL/POD from Create Customer Invoice (FROM / TO shipping location).
ALTER TABLE invoice_history ADD COLUMN pol VARCHAR(512) NULL;
ALTER TABLE invoice_history ADD COLUMN pod VARCHAR(512) NULL;
