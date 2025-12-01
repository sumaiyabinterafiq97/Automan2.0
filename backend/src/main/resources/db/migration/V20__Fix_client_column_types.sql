-- Fix column types in clients table to match the Client entity
-- Change DECIMAL columns to DOUBLE for Double fields in Kotlin

-- First, drop the existing columns
ALTER TABLE clients DROP COLUMN current_balance;
ALTER TABLE clients DROP COLUMN credit_limit;
ALTER TABLE clients DROP COLUMN alert_threshold;

-- Add them back with correct DOUBLE type
ALTER TABLE clients ADD COLUMN current_balance DOUBLE DEFAULT 0.0;
ALTER TABLE clients ADD COLUMN credit_limit DOUBLE;
ALTER TABLE clients ADD COLUMN alert_threshold DOUBLE;

-- Update the existing sample client with proper values
UPDATE clients SET 
    current_balance = 0.0,
    credit_limit = 100000.0,
    alert_threshold = 5000.0
WHERE id = 1;
