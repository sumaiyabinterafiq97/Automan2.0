-- Optional verified relationship. Existing clients stay unassigned.
-- Do not copy consignee text from client_map, booking_mappings, purchases, or shipping.

ALTER TABLE clients
    ADD COLUMN consignee_id BIGINT NULL,
    ADD KEY idx_clients_consignee_id (consignee_id),
    ADD CONSTRAINT fk_clients_consignee
        FOREIGN KEY (consignee_id) REFERENCES consignees (id);
