-- Rollback template for V53 (vehicle spec column drop)
-- Restore DB from backup-pre-v53-YYYYMMDD.sql for fastest rollback.
-- Use this script only if you must re-add columns without full restore.

ALTER TABLE purchases
  ADD COLUMN car_model_year VARCHAR(10) NULL AFTER date,
  ADD COLUMN shipment_size VARCHAR(50) NULL AFTER car_name,
  ADD COLUMN grade VARCHAR(100) NULL AFTER shipment_size,
  ADD COLUMN `rank` VARCHAR(100) NULL AFTER grade,
  ADD COLUMN color VARCHAR(100) NULL AFTER `rank`,
  ADD COLUMN fuel VARCHAR(100) NULL AFTER color,
  ADD COLUMN seat VARCHAR(50) NULL AFTER fuel,
  ADD COLUMN door VARCHAR(50) NULL AFTER seat,
  ADD COLUMN distance VARCHAR(100) NULL AFTER door,
  ADD COLUMN CC INT NULL AFTER options,
  ADD COLUMN shift VARCHAR(50) NULL AFTER CC,
  ADD COLUMN WD VARCHAR(50) NULL AFTER shift,
  ADD COLUMN drive_type VARCHAR(50) NULL AFTER WD;

-- Backfill from purchase_vehicle_overrides (override wins) + car_brand_mapping baseline
-- Example per-field (run per purchase or adapt as batch UPDATE JOIN):
-- UPDATE purchases p
-- JOIN purchase_vehicle_overrides o ON o.purchase_id = p.id
-- SET p.fuel = JSON_UNQUOTE(JSON_EXTRACT(o.overrides, '$.fuel'))
-- WHERE JSON_EXTRACT(o.overrides, '$.fuel') IS NOT NULL;

-- After column restore: redeploy pre-V53 backend JAR and verify GET/list/save.
