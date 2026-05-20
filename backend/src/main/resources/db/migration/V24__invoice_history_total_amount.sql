-- Total LIST amount from Create Customer Invoice (formatted e.g. ¥33,000).
ALTER TABLE invoice_history ADD COLUMN total_amount VARCHAR(128) NULL;
