-- Saved invoices from the Create Customer Invoice page (Confirm and Download PDF).
CREATE TABLE invoice_history (
    invoice_number VARCHAR(64) NOT NULL PRIMARY KEY,
    vessel VARCHAR(255),
    client_name VARCHAR(512),
    shipping_date DATE NULL,
    lc_no VARCHAR(512),
    bank TEXT,
    messages TEXT,
    chassis TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_invoice_history_invoice_number (invoice_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
