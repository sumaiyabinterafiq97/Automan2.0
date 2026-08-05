-- Data cleanup: strip redundant leading "Toyota" brand prefix from Chassis Map car_name.
-- Applies per semicolon-separated token, e.g.
--   "Toyota Corolla Spacio" -> "Corolla Spacio"
--   "Toyota Corolla Spacio;Toyota Corolla;ALLEX" -> "Corolla Spacio;Corolla;ALLEX"
-- Idempotent: only rows that still match the prefix pattern are updated.
-- Does not touch purchases or other tables.

UPDATE car_brand_mapping
SET car_name = NULLIF(
  TRIM(BOTH ' ' FROM TRIM(BOTH ';' FROM
    REGEXP_REPLACE(
      REGEXP_REPLACE(car_name, '(?i)(^|;)[[:space:]]*toyota[[:space:]-]+', '$1'),
      ';[[:space:]]*;',
      ';'
    )
  )),
  ''
)
WHERE car_name IS NOT NULL
  AND car_name REGEXP '(?i)(^|;)[[:space:]]*toyota[[:space:]-]+'
  AND TRIM(
    REGEXP_REPLACE(car_name, '(?i)(^|;)[[:space:]]*toyota[[:space:]-]+', '$1')
  ) <> '';
