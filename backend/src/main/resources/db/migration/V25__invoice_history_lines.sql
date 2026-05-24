-- One invoice_history header row per invoice_number; chassis and per-line amounts live in invoice_history_line.

ALTER TABLE invoice_history DROP INDEX uq_invoice_history_invoice_number;
ALTER TABLE invoice_history DROP PRIMARY KEY, ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE invoice_history ADD UNIQUE KEY uq_invoice_history_invoice_number (invoice_number);

CREATE TABLE invoice_history_line (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    invoice_history_id BIGINT NOT NULL,
    chassis VARCHAR(512) NOT NULL,
    line_amount VARCHAR(128) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_invoice_history_line_header FOREIGN KEY (invoice_history_id) REFERENCES invoice_history (id) ON DELETE CASCADE,
    INDEX idx_invoice_history_line_header (invoice_history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Split legacy semicolon-separated chassis into one row each; keep total_amount on first line only.
INSERT INTO invoice_history_line (invoice_history_id, chassis, line_amount, sort_order)
SELECT
    h.id,
    TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(h.chassis, ';', nums.n), ';', -1)) AS tok,
    CASE WHEN nums.n = 1 THEN h.total_amount ELSE NULL END,
    nums.n - 1
FROM invoice_history h
INNER JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20
    UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION SELECT 30
) nums
    ON h.chassis IS NOT NULL
    AND TRIM(h.chassis) <> ''
    AND nums.n <= (CHAR_LENGTH(h.chassis) - CHAR_LENGTH(REPLACE(h.chassis, ';', '')) + 1)
WHERE TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(h.chassis, ';', nums.n), ';', -1)) <> '';

ALTER TABLE invoice_history DROP COLUMN chassis;
ALTER TABLE invoice_history DROP COLUMN total_amount;
