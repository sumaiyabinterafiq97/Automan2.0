-- Remove steering_wheel column from purchases (redundant with drive_type: LHD/RHD)
ALTER TABLE purchases DROP COLUMN steering_wheel;
