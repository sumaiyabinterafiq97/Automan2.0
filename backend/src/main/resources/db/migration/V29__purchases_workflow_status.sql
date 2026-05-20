-- Canonical workflow derived from lifecycle flags (see PurchaseWorkflowService).
ALTER TABLE purchases ADD COLUMN workflow_status VARCHAR(32) NULL;
ALTER TABLE purchases ADD COLUMN workflow_status_updated_at TIMESTAMP NULL;

UPDATE purchases SET workflow_status = CASE
    WHEN invoice_confirmed IS NOT NULL AND invoice_confirmed = TRUE THEN 'INVOICE_CONFIRMED'
    WHEN booking_requested = TRUE THEN 'BOOKING_REQUESTED'
    WHEN UPPER(TRIM(COALESCE(rixo_confirmed, ''))) IN ('TRUE', '1') THEN 'RIXO_CONFIRMED'
    WHEN UPPER(TRIM(COALESCE(rixo_requested, ''))) IN ('TRUE', '1') THEN 'RIXO_REQUESTED'
    ELSE 'PURCHASED'
END;

UPDATE purchases
SET workflow_status_updated_at = CURRENT_TIMESTAMP
WHERE workflow_status IS NOT NULL;
