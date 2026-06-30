-- Rollback template for V54 (workflow legacy flag drop)
-- Restore DB from backup-pre-v54-YYYYMMDD.sql for fastest rollback.

ALTER TABLE purchases
  ADD COLUMN rixo_requested VARCHAR(50) NULL AFTER payment_date,
  ADD COLUMN rixo_confirmed VARCHAR(50) NULL AFTER rixo_requested,
  ADD COLUMN booking_requested TINYINT(1) NOT NULL DEFAULT 0 AFTER vessel,
  ADD COLUMN invoice_confirmed TINYINT(1) NULL AFTER booking_requested;

-- Backfill from workflow_status (example)
-- UPDATE purchases SET rixo_requested = 'TRUE' WHERE workflow_status IN ('RIXO_REQUESTED','RIXO_CONFIRMED','BOOKING_REQUESTED','INVOICE_CONFIRMED');
-- UPDATE purchases SET rixo_confirmed = 'TRUE' WHERE workflow_status IN ('RIXO_CONFIRMED','BOOKING_REQUESTED','INVOICE_CONFIRMED');
-- UPDATE purchases SET booking_requested = 1 WHERE workflow_status IN ('BOOKING_REQUESTED','INVOICE_CONFIRMED');
-- UPDATE purchases SET invoice_confirmed = 1 WHERE workflow_status = 'INVOICE_CONFIRMED';

-- After column restore: redeploy pre-V54 backend JAR and verify booking/invoice flows.
