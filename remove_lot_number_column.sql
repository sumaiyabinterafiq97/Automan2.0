-- Migration script to remove lot_number column from purchases table
-- This script safely removes the lot_number column and related constraints

-- Step 1: Remove the unique constraint that includes lot_number
ALTER TABLE purchases DROP INDEX uk_lot_chassis;

-- Step 2: Remove the lot_number column
ALTER TABLE purchases DROP COLUMN lot_number;

-- Step 3: Verify the changes
-- The table should now have the lot_number column and uk_lot_chassis constraint removed
-- The uk_chassis constraint (chassis only) should remain intact

-- Note: This migration is irreversible - lot_number data will be permanently lost
-- Make sure to backup your database before running this script
