-- Move any remaining data from legacy vessel_no into vessel, then drop vessel_no.
-- UI and API use the `vessel` column only.

UPDATE purchases
SET vessel = vessel_no
WHERE (vessel IS NULL OR TRIM(vessel) = '')
  AND vessel_no IS NOT NULL
  AND TRIM(vessel_no) != '';

ALTER TABLE purchases DROP COLUMN vessel_no;
