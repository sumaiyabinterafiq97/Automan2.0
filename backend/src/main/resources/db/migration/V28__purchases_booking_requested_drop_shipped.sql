-- Replace shipped with booking_requested (backfill from shipped, then drop shipped)
ALTER TABLE purchases ADD COLUMN booking_requested TINYINT(1) NOT NULL DEFAULT 0;

UPDATE purchases
SET booking_requested = CASE
    WHEN shipped IS NULL OR shipped = 0 OR shipped = FALSE THEN 0
    ELSE 1
END;

ALTER TABLE purchases DROP COLUMN shipped;
