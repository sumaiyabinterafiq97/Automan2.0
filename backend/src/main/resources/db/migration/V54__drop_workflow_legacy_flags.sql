-- Phase 5 drop 2: workflow_status is canonical; legacy flag columns removed.
-- Rollback: docs/diagnostics/rollback-v54-template.sql

ALTER TABLE purchases
  DROP COLUMN rixo_requested,
  DROP COLUMN rixo_confirmed,
  DROP COLUMN booking_requested,
  DROP COLUMN invoice_confirmed;
