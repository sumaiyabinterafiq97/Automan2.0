-- Explicit portal access. No rows are inserted.
-- party_id is clients.id when party_type is CLIENT, and consignees.id when it is CONSIGNEE.
-- MySQL cannot point one foreign key at two tables, so client_id and consignee_id hold the
-- real references and the CHECK requires party_id to match the column for that type.
-- A unique index cannot be partial in MySQL. active_user_type is NULL unless the row is
-- ACTIVE, and a unique index allows many NULLs, so only one ACTIVE row per user and party type.

CREATE TABLE user_party_links (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    party_type VARCHAR(16) NOT NULL,
    party_id BIGINT NOT NULL,
    client_id BIGINT NULL,
    consignee_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    linked_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at DATETIME(6) NULL,
    active_user_type VARCHAR(48) GENERATED ALWAYS AS (
        CASE
            WHEN status = 'ACTIVE' THEN CONCAT(user_id, ':', party_type)
            ELSE NULL
        END
    ) STORED,
    CONSTRAINT fk_user_party_links_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_party_links_linked_by
        FOREIGN KEY (linked_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_user_party_links_client
        FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_user_party_links_consignee
        FOREIGN KEY (consignee_id) REFERENCES consignees (id),
    CONSTRAINT chk_user_party_links_type CHECK (party_type IN ('CLIENT', 'CONSIGNEE')),
    CONSTRAINT chk_user_party_links_status CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    CONSTRAINT chk_user_party_links_target CHECK (
        (party_type = 'CLIENT' AND client_id = party_id AND consignee_id IS NULL)
        OR
        (party_type = 'CONSIGNEE' AND consignee_id = party_id AND client_id IS NULL)
    ),
    CONSTRAINT chk_user_party_links_revoked_at CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL)
        OR
        (status <> 'REVOKED' AND revoked_at IS NULL)
    ),
    UNIQUE KEY uk_user_party_links_one_active_type (active_user_type),
    KEY idx_user_party_links_user_status (user_id, status),
    KEY idx_user_party_links_party (party_type, party_id, status),
    KEY idx_user_party_links_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
