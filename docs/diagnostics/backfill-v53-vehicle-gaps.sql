-- Pre-V53 gate backfill: purchase_vehicle_overrides for vehicle_dual_write_gaps
-- Run when phase5-drop-vehicle-gates.txt shows gaps > 0
-- Date: 2026-06-29

INSERT INTO purchase_vehicle_overrides (purchase_id, overrides, created_at, updated_at)
SELECT 1, '{"distance":"200","wd":"2WD","driveType":"RHD"}', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM purchase_vehicle_overrides WHERE purchase_id = 1);

INSERT INTO purchase_vehicle_overrides (purchase_id, overrides, created_at, updated_at)
SELECT 2, '{"carModelYear":"2026-07","grade":"X","fuel":"GASOLINE","door":"5","cc":"2400","wd":"4WD","driveType":"RHD"}', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM purchase_vehicle_overrides WHERE purchase_id = 2);

-- Verify gate
SELECT COUNT(*) AS vehicle_dual_write_gaps
FROM purchases p
WHERE p.fuel IS NOT NULL AND TRIM(p.fuel) <> ''
  AND NOT EXISTS (SELECT 1 FROM purchase_vehicle_overrides o WHERE o.purchase_id = p.id);
