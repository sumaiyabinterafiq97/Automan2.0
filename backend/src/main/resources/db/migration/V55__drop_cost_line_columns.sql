-- Phase 5 drop 3: cost/fee canonical on purchase_cost_lines (by cost_code).
-- Rollback: docs/diagnostics/rollback-v55-template.sql
-- KEEP: total_price, repair_company

ALTER TABLE purchases
  DROP COLUMN price,
  DROP COLUMN auction_fee,
  DROP COLUMN auction_penalty_fee,
  DROP COLUMN recycle_fee,
  DROP COLUMN road_tax,
  DROP COLUMN tax_total,
  DROP COLUMN shipment_charges,
  DROP COLUMN freight,
  DROP COLUMN storage_charges,
  DROP COLUMN misc_charges,
  DROP COLUMN inspection_fee,
  DROP COLUMN commission,
  DROP COLUMN rixo_price,
  DROP COLUMN repair_charges,
  DROP COLUMN profit;
