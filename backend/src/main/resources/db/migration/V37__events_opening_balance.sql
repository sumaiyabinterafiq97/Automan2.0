-- One-time migration ledger line for imported starting balances.
ALTER TABLE events MODIFY COLUMN event_type ENUM(
    'PAYMENT_RECEIVED',
    'SHIPMENT',
    'ADJUSTMENT',
    'OTHER',
    'INVOICE_ISSUED',
    'INVOICE_REVERSAL',
    'OPENING_BALANCE'
) NOT NULL;
