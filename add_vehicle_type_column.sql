-- Add vehicle_type column to purchases table
ALTER TABLE purchases ADD COLUMN vehicle_type VARCHAR(50) AFTER car_name;
