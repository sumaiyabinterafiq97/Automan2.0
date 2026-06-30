-- Phase 4 drop 2: shipping snapshot canonical on shipping_history (vessel, date, B/L).
-- Rollback: re-add nullable columns + backfill from shipping_history by chassis.

ALTER TABLE purchases
  DROP COLUMN vessel,
  DROP COLUMN shippment_date,
  DROP COLUMN `B/L_no`;
