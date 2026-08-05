-- Chassis Map multi-value grade/rank chips exceed VARCHAR(255).
-- Same pattern as recycle_fee / chassis_number (TEXT for semicolon-joined cells).
ALTER TABLE car_brand_mapping MODIFY grade TEXT NULL;
ALTER TABLE car_brand_mapping MODIFY `rank` TEXT NULL;
