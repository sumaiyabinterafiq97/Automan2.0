-- Phase 2: Backfill cost lines from legacy purchases columns (idempotent).
-- Source: purchase_field_registry cost_code_or_json_key for COST_LINE classification.

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'PRICE',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.price, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  1
FROM purchases p
WHERE p.price IS NOT NULL AND TRIM(p.price) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'AUCTION_FEE',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.auction_fee, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  2
FROM purchases p
WHERE p.auction_fee IS NOT NULL AND TRIM(p.auction_fee) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'AUCTION_PENALTY_FEE',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.auction_penalty_fee, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  3
FROM purchases p
WHERE p.auction_penalty_fee IS NOT NULL AND TRIM(p.auction_penalty_fee) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'RECYCLE_FEE',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.recycle_fee, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  4
FROM purchases p
WHERE p.recycle_fee IS NOT NULL AND TRIM(p.recycle_fee) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'ROAD_TAX',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.road_tax, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  5
FROM purchases p
WHERE p.road_tax IS NOT NULL AND TRIM(p.road_tax) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'TAX_TOTAL',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.tax_total, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  6
FROM purchases p
WHERE p.tax_total IS NOT NULL AND TRIM(p.tax_total) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'TOTAL_PRICE',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.total_price, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  7
FROM purchases p
WHERE p.total_price IS NOT NULL AND TRIM(p.total_price) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'SHIPMENT_CHARGES',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.shipment_charges, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  8
FROM purchases p
WHERE p.shipment_charges IS NOT NULL AND TRIM(p.shipment_charges) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'FREIGHT',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.freight, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  9
FROM purchases p
WHERE p.freight IS NOT NULL AND TRIM(p.freight) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'STORAGE_CHARGES',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.storage_charges, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  10
FROM purchases p
WHERE p.storage_charges IS NOT NULL AND TRIM(p.storage_charges) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'MISC_CHARGES',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.misc_charges, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  11
FROM purchases p
WHERE p.misc_charges IS NOT NULL AND TRIM(p.misc_charges) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'INSPECTION_FEE',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.inspection_fee, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  12
FROM purchases p
WHERE p.inspection_fee IS NOT NULL AND TRIM(p.inspection_fee) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'COMMISSION',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.commission, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  13
FROM purchases p
WHERE p.commission IS NOT NULL AND TRIM(p.commission) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'RIXO_PRICE',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.rixo_price, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  14
FROM purchases p
WHERE p.rixo_price IS NOT NULL AND TRIM(p.rixo_price) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'REPAIR_CHARGES',
  CAST(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(COALESCE(p.repair_charges, ''), ',', ''), '¥', ''), 'Â¥', '')), '') AS DECIMAL(15,2)),
  15
FROM purchases p
WHERE p.repair_charges IS NOT NULL AND TRIM(p.repair_charges) <> ''
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);

INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order)
SELECT p.id, 'PROFIT', COALESCE(p.profit, 0), 16
FROM purchases p
WHERE p.profit IS NOT NULL
ON DUPLICATE KEY UPDATE amount = VALUES(amount), sort_order = VALUES(sort_order);
