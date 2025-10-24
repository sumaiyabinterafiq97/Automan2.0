-- Migration script to add tax_total column to purchases table
-- This script adds the tax_total column for storing calculated tax amounts

-- Add the tax_total column
ALTER TABLE purchases ADD COLUMN tax_total VARCHAR(50) NULL;

-- The table should now have the tax_total column added
-- This column will store the calculated 10% tax value
