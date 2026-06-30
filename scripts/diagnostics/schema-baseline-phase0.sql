-- Automan 2.0 — Phase 0 baseline (read-only)
-- Source: docs/schema-consolidation-phase-gates.md (Global Diagnostic Pack + Phase 0)
-- Usage: docker exec -i automan_mysql_multiplatform mysql -u automan_user -pautoman_password automan_car_purchase -t < scripts/diagnostics/schema-baseline-phase0.sql

SELECT '=== 1) Flyway state ===' AS section;
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT '=== 2) Table inventory ===' AS section;
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = 'automan_car_purchase'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

SELECT '=== 3) purchases column count ===' AS section;
SELECT COUNT(*) AS purchases_column_count
FROM information_schema.columns
WHERE table_schema = 'automan_car_purchase' AND table_name = 'purchases';

SELECT '=== 4) Duplicate chassis (top 20) ===' AS section;
SELECT chassis, COUNT(*) AS cnt
FROM purchases
GROUP BY chassis
HAVING COUNT(*) > 1
ORDER BY cnt DESC
LIMIT 20;

SELECT '=== 5) Orphan client_id on purchases ===' AS section;
SELECT COUNT(*) AS orphan_client_ids
FROM purchases p
LEFT JOIN clients c ON p.client_id = c.id
WHERE p.client_id IS NOT NULL AND c.id IS NULL;

SELECT '=== 6) Shipping without purchase ===' AS section;
SELECT COUNT(*) AS shipping_without_purchase
FROM shipping_history sh
LEFT JOIN purchases p ON p.chassis = sh.chassis
WHERE p.id IS NULL;

SELECT '=== 7) Invoice lines without purchase ===' AS section;
SELECT COUNT(*) AS invoice_lines_without_purchase
FROM invoice_history_line il
LEFT JOIN purchases p ON p.chassis = il.chassis
WHERE p.id IS NULL;

SELECT '=== 8) NULL workflow_status ===' AS section;
SELECT COUNT(*) AS null_workflow_status
FROM purchases
WHERE workflow_status IS NULL;

SELECT '=== 9) INVOICE_ISSUED without invoice_number ===' AS section;
SELECT COUNT(*) AS invoice_issued_missing_number
FROM events
WHERE event_type = 'INVOICE_ISSUED' AND (invoice_number IS NULL OR TRIM(invoice_number) = '');

SELECT '=== 10) Orphan ledger events ===' AS section;
SELECT COUNT(*) AS orphan_events
FROM events e
LEFT JOIN clients c ON e.client_id = c.id
WHERE c.id IS NULL;

SELECT '=== 11) Booking-eligible pool size ===' AS section;
SELECT COUNT(*) AS booking_eligible_count
FROM purchases p
WHERE (p.booking_requested IS NULL OR p.booking_requested = 0)
  AND UPPER(TRIM(COALESCE(p.rixo_confirmed, ''))) IN ('TRUE', '1');

SELECT '=== 12) Invoice-pending pool ===' AS section;
SELECT COUNT(*) AS invoice_pending_count
FROM purchases p
WHERE (p.invoice_confirmed IS NULL OR p.invoice_confirmed = 0)
  AND (p.booking_requested IS NULL OR p.booking_requested = 0);

SELECT '=== A) Vehicle spec vs map mismatch (sample 50) ===' AS section;
SELECT p.id, p.chassis, p.fuel AS purchase_fuel, m.fuel AS map_fuel
FROM purchases p
JOIN car_brand_mapping m ON p.chassis LIKE CONCAT(m.chassis, '%')
WHERE p.fuel IS NOT NULL AND m.fuel IS NOT NULL
  AND TRIM(p.fuel) <> '' AND TRIM(m.fuel) <> ''
  AND LOWER(TRIM(SUBSTRING_INDEX(m.fuel, ';', 1))) <> LOWER(TRIM(p.fuel))
LIMIT 50;

SELECT '=== B) Vehicle spec population ===' AS section;
SELECT
  COUNT(*) AS total,
  SUM(CASE WHEN fuel IS NOT NULL AND TRIM(fuel) <> '' THEN 1 ELSE 0 END) AS has_fuel,
  SUM(CASE WHEN grade IS NOT NULL AND TRIM(grade) <> '' THEN 1 ELSE 0 END) AS has_grade,
  SUM(CASE WHEN color IS NOT NULL AND TRIM(color) <> '' THEN 1 ELSE 0 END) AS has_color
FROM purchases;

SELECT '=== C) Cost column population ===' AS section;
SELECT
  SUM(CASE WHEN price IS NOT NULL AND TRIM(price) <> '' THEN 1 ELSE 0 END) AS has_price,
  SUM(CASE WHEN auction_fee IS NOT NULL AND TRIM(auction_fee) <> '' THEN 1 ELSE 0 END) AS has_auction_fee,
  SUM(CASE WHEN freight IS NOT NULL AND TRIM(freight) <> '' THEN 1 ELSE 0 END) AS has_freight,
  SUM(CASE WHEN total_price IS NOT NULL AND TRIM(total_price) <> '' THEN 1 ELSE 0 END) AS has_total_price
FROM purchases;

SELECT '=== D) Workflow flag drift ===' AS section;
SELECT workflow_status,
  SUM(CASE WHEN UPPER(TRIM(COALESCE(rixo_confirmed,''))) IN ('TRUE','1') THEN 1 ELSE 0 END) AS legacy_rixo_confirmed,
  SUM(CASE WHEN booking_requested = 1 THEN 1 ELSE 0 END) AS legacy_booking_requested,
  SUM(CASE WHEN invoice_confirmed = 1 THEN 1 ELSE 0 END) AS legacy_invoice_confirmed,
  COUNT(*) AS cnt
FROM purchases
GROUP BY workflow_status
ORDER BY cnt DESC;

SELECT '=== E) workflow_legacy_mismatch ===' AS section;
SELECT COUNT(*) AS workflow_legacy_mismatch
FROM purchases
WHERE workflow_status = 'RIXO_CONFIRMED'
  AND UPPER(TRIM(COALESCE(rixo_confirmed, ''))) NOT IN ('TRUE', '1');

SELECT '=== F) Top edited fields (top 30) ===' AS section;
SELECT field_name, COUNT(*) AS edit_count
FROM purchase_change_history
GROUP BY field_name
ORDER BY edit_count DESC
LIMIT 30;

SELECT '=== G) purchase_change_history volume ===' AS section;
SELECT COUNT(*) AS total_audit_rows,
       COUNT(DISTINCT purchase_id) AS purchases_with_edits
FROM purchase_change_history;
