-- Phase 1: Idempotent backfill of purchases.workflow_status from legacy flags.
-- Same precedence as V29 / PurchaseWorkflowService:
--   INVOICE_CONFIRMED → BOOKING_REQUESTED → RIXO_CONFIRMED → RIXO_REQUESTED → PURCHASED
-- Only rows with NULL workflow_status are updated (safe to re-run).
-- Rollback: no schema change; optional manual UPDATE workflow_status = NULL where backfilled in this migration is not practical — restore from backup if needed.

UPDATE purchases
SET workflow_status = CASE
    WHEN invoice_confirmed IS NOT NULL AND invoice_confirmed = TRUE THEN 'INVOICE_CONFIRMED'
    WHEN booking_requested = TRUE THEN 'BOOKING_REQUESTED'
    WHEN UPPER(TRIM(COALESCE(rixo_confirmed, ''))) IN ('TRUE', '1') THEN 'RIXO_CONFIRMED'
    WHEN UPPER(TRIM(COALESCE(rixo_requested, ''))) IN ('TRUE', '1') THEN 'RIXO_REQUESTED'
    ELSE 'PURCHASED'
END
WHERE workflow_status IS NULL;

UPDATE purchases
SET workflow_status_updated_at = CURRENT_TIMESTAMP
WHERE workflow_status IS NOT NULL
  AND workflow_status_updated_at IS NULL;
