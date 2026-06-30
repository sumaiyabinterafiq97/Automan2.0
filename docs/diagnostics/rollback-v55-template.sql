-- Rollback template for V55 (cost/fee column drop)
-- Restore DB from backup-pre-v55-YYYYMMDD.sql for fastest rollback.
-- Use this script only if you must re-add columns without full restore.

ALTER TABLE purchases
  ADD COLUMN price VARCHAR(50) NULL AFTER country,
  ADD COLUMN auction_fee VARCHAR(50) NULL AFTER price,
  ADD COLUMN auction_penalty_fee VARCHAR(50) NULL AFTER auction_fee,
  ADD COLUMN recycle_fee VARCHAR(50) NULL AFTER auction_penalty_fee,
  ADD COLUMN road_tax VARCHAR(50) NULL AFTER recycle_fee,
  ADD COLUMN tax_total VARCHAR(50) NULL AFTER road_tax,
  ADD COLUMN shipment_charges VARCHAR(50) NULL AFTER workflow_status_updated_at,
  ADD COLUMN freight VARCHAR(50) NULL AFTER shipment_charges,
  ADD COLUMN storage_charges VARCHAR(50) NULL AFTER freight,
  ADD COLUMN misc_charges VARCHAR(50) NULL AFTER storage_charges,
  ADD COLUMN inspection_fee VARCHAR(50) NULL AFTER misc_charges,
  ADD COLUMN commission VARCHAR(50) NULL AFTER inspection_fee,
  ADD COLUMN rixo_price VARCHAR(50) NULL AFTER commission,
  ADD COLUMN repair_charges VARCHAR(50) NULL AFTER repair_company,
  ADD COLUMN profit DECIMAL(15,2) NULL AFTER repair_charges;

-- Backfill from purchase_cost_lines (example per cost_code)
-- UPDATE purchases p
-- JOIN purchase_cost_lines c ON c.purchase_id = p.id AND c.cost_code = 'PRICE'
-- SET p.price = CAST(c.amount AS CHAR);
-- (repeat for AUCTION_FEE, FREIGHT, PROFIT, etc.)

-- After column restore: redeploy pre-V55 backend JAR and verify C&F, list, import, PDF.
