-- Phase 4 drop 1: extended_attributes is canonical; legacy columns removed.
-- Rollback: re-add nullable columns + backfill from extended_attributes JSON.

ALTER TABLE purchases
  DROP COLUMN options,
  DROP COLUMN auction_no,
  DROP COLUMN payment_date,
  DROP COLUMN notes,
  DROP COLUMN venue_id,
  DROP COLUMN number_cut,
  DROP COLUMN shaken,
  DROP COLUMN negotiate,
  DROP COLUMN is_package_mode,
  DROP COLUMN car_pictures;
