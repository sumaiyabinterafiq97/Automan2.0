-- Repair: Docker/init DBs baselined at Flyway V37 without running V26/V36/V37.
-- Invoice save posts INVOICE_ISSUED; missing ENUM values cause "Data truncated for column 'event_type'".
-- Idempotent: safe on DBs that already applied V26–V37.

ALTER TABLE events MODIFY COLUMN event_type ENUM(
    'PAYMENT_RECEIVED',
    'SHIPMENT',
    'ADJUSTMENT',
    'OTHER',
    'INVOICE_ISSUED',
    'INVOICE_REVERSAL',
    'OPENING_BALANCE'
) NOT NULL;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'events' AND column_name = 'invoice_number'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE events ADD COLUMN invoice_number VARCHAR(64) NULL AFTER bill_number',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'events' AND index_name = 'idx_events_client_invoice'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_events_client_invoice ON events (client_id, invoice_number)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
