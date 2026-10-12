-- One row, id = 1, is the first-admin claim.
-- InnoDB locks the primary key, so two concurrent setup inserts cannot both succeed.
-- MySQL 8.0 enforces the CHECK constraint. Existing installs are unchanged until setup runs.

CREATE TABLE IF NOT EXISTS auth_bootstrap (
    id TINYINT NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_auth_bootstrap_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
