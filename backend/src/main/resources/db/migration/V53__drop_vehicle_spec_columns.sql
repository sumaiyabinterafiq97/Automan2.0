-- Phase 5 drop 1: vehicle spec canonical on purchase_vehicle_overrides + car_brand_mapping.
-- Rollback: docs/diagnostics/rollback-v53-template.sql

ALTER TABLE purchases
  DROP COLUMN car_model_year,
  DROP COLUMN shipment_size,
  DROP COLUMN grade,
  DROP COLUMN `rank`,
  DROP COLUMN color,
  DROP COLUMN fuel,
  DROP COLUMN seat,
  DROP COLUMN door,
  DROP COLUMN distance,
  DROP COLUMN CC,
  DROP COLUMN shift,
  DROP COLUMN WD,
  DROP COLUMN drive_type;
