-- Mark whether a purchase has been confirmed in Customer Invoice flow.
ALTER TABLE purchases
    ADD COLUMN invoice_confirmed BOOLEAN DEFAULT FALSE;

