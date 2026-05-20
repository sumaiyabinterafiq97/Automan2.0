-- Purchase lifecycle: marked sold (independent of invoice confirmation).
ALTER TABLE purchases
    ADD COLUMN sold BOOLEAN DEFAULT FALSE;
