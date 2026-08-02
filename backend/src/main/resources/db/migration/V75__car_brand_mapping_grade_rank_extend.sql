-- Chassis Map: multi-value semicolon-separated grade/rank chips exceed VARCHAR(50).
-- Same pattern as V10 for cc/door/seat (multi-token cells).
ALTER TABLE car_brand_mapping MODIFY grade VARCHAR(255) NULL;
ALTER TABLE car_brand_mapping MODIFY `rank` VARCHAR(255) NULL;
