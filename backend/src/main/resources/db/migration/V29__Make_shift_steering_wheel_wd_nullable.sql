-- Make shift, steering_wheel, and WD columns nullable (only chassis should be required)
ALTER TABLE purchases MODIFY COLUMN shift VARCHAR(50) NULL;
ALTER TABLE purchases MODIFY COLUMN steering_wheel VARCHAR(20) NULL;
ALTER TABLE purchases MODIFY COLUMN WD VARCHAR(20) NULL;

