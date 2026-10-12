-- V3 widened users.role to VARCHAR(16), but databases baselined at V37 never ran V3.
-- Those databases still have ENUM('VIEWER','EDITOR','ADMIN'). This ALTER keeps every
-- existing value and allows CLIENT and CONSIGNEE. It does not rebuild the users table.

ALTER TABLE users
    MODIFY COLUMN role VARCHAR(16) NOT NULL DEFAULT 'VIEWER';
