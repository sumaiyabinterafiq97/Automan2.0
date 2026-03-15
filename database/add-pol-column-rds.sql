-- Add missing 'pol' column to purchases table on RDS (one-time migration).
-- Run this on EC2: mysql -h $RDS_ENDPOINT -u $RDS_USER -p$RDS_PASSWORD automan_car_purchase < database/add-pol-column-rds.sql

USE automan_car_purchase;

-- Add pol column if missing (safe to run multiple times: fails silently if column exists)
ALTER TABLE purchases ADD COLUMN pol VARCHAR(100) NULL AFTER stock_location;
