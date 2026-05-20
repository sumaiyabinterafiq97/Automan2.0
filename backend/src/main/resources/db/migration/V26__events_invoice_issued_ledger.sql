-- Client ledger: invoice confirm posts INVOICE_ISSUED events; optional invoice_number for idempotency.
ALTER TABLE events MODIFY COLUMN event_type ENUM(
    'PAYMENT_RECEIVED',
    'SHIPMENT',
    'ADJUSTMENT',
    'OTHER',
    'INVOICE_ISSUED'
) NOT NULL;

ALTER TABLE events
    ADD COLUMN invoice_number VARCHAR(64) NULL AFTER bill_number;

CREATE INDEX idx_events_client_invoice ON events (client_id, invoice_number);
