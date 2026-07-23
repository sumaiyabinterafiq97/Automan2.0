-- Ensure Consignee Map notify / in-transit are full TEXT (not TINYTEXT).
ALTER TABLE booking_mappings MODIFY COLUMN notify_party TEXT NULL;
ALTER TABLE booking_mappings MODIFY COLUMN in_transit_clause TEXT NULL;
ALTER TABLE shipping_history MODIFY COLUMN notify_party TEXT NULL;
ALTER TABLE shipping_history MODIFY COLUMN in_transit_clause TEXT NULL;
