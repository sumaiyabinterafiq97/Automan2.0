-- clients.consignee_id has no actor or history. This table records each staff change.
-- consignee_id is the new value. NULL means the assignment was cleared.
-- No rows are inserted. Existing clients stay unassigned.

CREATE TABLE client_consignee_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    previous_consignee_id BIGINT NULL,
    consignee_id BIGINT NULL,
    changed_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_client_consignee_assignments_client
        FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_client_consignee_assignments_previous
        FOREIGN KEY (previous_consignee_id) REFERENCES consignees (id),
    CONSTRAINT fk_client_consignee_assignments_consignee
        FOREIGN KEY (consignee_id) REFERENCES consignees (id),
    CONSTRAINT fk_client_consignee_assignments_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users (id),
    KEY idx_client_consignee_assignments_client (client_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
