-- Client ledger: invoice delete / re-save posts INVOICE_REVERSAL to credit back charges.
ALTER TABLE events MODIFY COLUMN event_type ENUM(
    'PAYMENT_RECEIVED',
    'SHIPMENT',
    'ADJUSTMENT',
    'OTHER',
    'INVOICE_ISSUED',
    'INVOICE_REVERSAL'
) NOT NULL;
