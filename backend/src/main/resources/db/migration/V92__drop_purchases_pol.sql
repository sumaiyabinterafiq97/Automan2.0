-- POL for a shipment is chosen on Create Shipping from Stock Location Map
-- and stored on shipping_history. Purchases no longer keep their own POL.
ALTER TABLE purchases DROP COLUMN pol;
