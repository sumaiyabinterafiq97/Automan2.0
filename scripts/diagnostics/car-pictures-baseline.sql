-- Phase 0 baseline audit for car pictures stored in purchases.extended_attributes.carPictures
-- Run on Railway production (read-only) and save output to docs/diagnostics/car-pictures-baseline.txt

SELECT COUNT(*) AS purchases_with_pictures
FROM purchases
WHERE extended_attributes IS NOT NULL
  AND JSON_EXTRACT(extended_attributes, '$.carPictures') IS NOT NULL
  AND TRIM(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures'))) NOT IN ('', '[]', 'null');

SELECT
  SUM(CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures')))) AS total_chars
FROM purchases
WHERE JSON_EXTRACT(extended_attributes, '$.carPictures') IS NOT NULL;

SELECT
  CASE
    WHEN CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures'))) < 100000 THEN '< 100 KB'
    WHEN CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures'))) < 1000000 THEN '100 KB - 1 MB'
    WHEN CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures'))) < 10000000 THEN '1 - 10 MB'
    ELSE '> 10 MB'
  END AS size_bucket,
  COUNT(*) AS purchase_count
FROM purchases
WHERE JSON_EXTRACT(extended_attributes, '$.carPictures') IS NOT NULL
  AND TRIM(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures'))) NOT IN ('', '[]', 'null')
GROUP BY size_bucket
ORDER BY purchase_count DESC;
