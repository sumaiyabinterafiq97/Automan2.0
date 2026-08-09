-- Mark whether a purchase has been confirmed in Customer Invoice flow.
SET @purchase_invoice_confirmed := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'purchases'
      AND COLUMN_NAME = 'invoice_confirmed'
);

SET @add_purchase_invoice_confirmed := IF(
    @purchase_invoice_confirmed = 0,
    'ALTER TABLE `purchases` ADD COLUMN `invoice_confirmed` BOOLEAN DEFAULT FALSE',
    'SELECT 1'
);

PREPARE stmt_purchase_invoice_confirmed FROM @add_purchase_invoice_confirmed;
EXECUTE stmt_purchase_invoice_confirmed;
DEALLOCATE PREPARE stmt_purchase_invoice_confirmed;

