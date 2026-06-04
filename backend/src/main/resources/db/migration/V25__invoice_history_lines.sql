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

-- Split all legacy semicolon-separated chassis into one row each; keep total_amount on first line only.
INSERT INTO invoice_history_line (invoice_history_id, chassis, line_amount, sort_order)
WITH RECURSIVE nums(n, max_n) AS (
    SELECT
        1,
        COALESCE((
            SELECT MAX(CHAR_LENGTH(chassis) - CHAR_LENGTH(REPLACE(chassis, ';', '')) + 1)
            FROM invoice_history
            WHERE chassis IS NOT NULL
              AND TRIM(chassis) <> ''
        ), 0)
    UNION ALL
    SELECT n + 1, max_n
    FROM nums
    WHERE n < max_n
)
SELECT
    h.id,
    TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(h.chassis, ';', nums.n), ';', -1)) AS tok,
    CASE WHEN nums.n = 1 THEN h.total_amount ELSE NULL END,
    nums.n - 1
FROM invoice_history h
INNER JOIN nums
    ON h.chassis IS NOT NULL
    AND TRIM(h.chassis) <> ''
    AND nums.n <= (CHAR_LENGTH(h.chassis) - CHAR_LENGTH(REPLACE(h.chassis, ';', '')) + 1)
WHERE TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(h.chassis, ';', nums.n), ';', -1)) <> '';

ALTER TABLE invoice_history DROP COLUMN chassis;
ALTER TABLE invoice_history DROP COLUMN total_amount;
