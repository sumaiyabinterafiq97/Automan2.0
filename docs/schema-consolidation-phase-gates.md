# Automan Schema Consolidation — Phase Gate Playbook

Manual QA steps, diagnostic SQL, risk controls, and go/no-go criteria for each phase of the `purchases` column consolidation initiative.

**Related:** [purchases-field-classification.csv](./purchases-field-classification.csv) — field-by-field KEEP / migrate / drop matrix.  
**Scope freeze:** [schema-consolidation-scope-freeze.md](./schema-consolidation-scope-freeze.md) — no new `purchases` columns until Phase 1 prod.

**Audience:** DBA, backend lead, QA before each release.

**Environment:** Run on **staging with a prod-sized copy** before promoting or starting the next phase.

---

## Global rules (every phase)

### Before you start any phase

| # | Action | Why |
|---|--------|-----|
| 1 | **Full DB backup** (RDS snapshot or `mysqldump`) | Rollback safety |
| 2 | **Record baseline** — run [Global Diagnostic Pack](#global-diagnostic-pack) below; save results to a file | Compare after each deploy |
| 3 | **Staging = prod shape** — same row counts order of magnitude, real chassis/client names | Catches query regressions |
| 4 | **One phase per release** — no combining Phase 2 + 3 | Isolates failures |
| 5 | **Feature flag or config** for read-path switch (if implemented) | Instant rollback without redeploy |
| 6 | **Freeze new `purchases` columns** for that release window | Avoid moving target |

### Non-negotiable safeguards (stop functionality risk)

| Safeguard | Required until |
|-----------|----------------|
| **API JSON shape unchanged** — responses still include `fuel`, `price`, `rixoConfirmed`, etc. | All phases complete + 1 release buffer |
| **Dual-write** — old columns populated when new tables/JSON updated | Phase read-switch + 2 weeks prod soak |
| **No column drops** in same release as read-switch | Column drop = separate gated release |
| **Never modify** `events`, `invoice_history`, `invoice_history_line` structure in this initiative | Permanent |
| **Keep** `chassis`, `client_id`, `booking_id`, `country`, `pol`, `workflow_status` queryable as columns | Until proven indexed alternatives exist |
| **CSV import** must still accept existing file headers | Until ops signs off on new template |
| **Ledger idempotency** — `INVOICE_ISSUED` per `(client_id, invoice_number)` unchanged | Permanent |

### Tables never in scope for consolidation

- `events` (client ledger)
- `invoice_history`, `invoice_history_line`
- `users`, `pending_signups`, `clients`
- `shipping_history` — keep UNIQUE `chassis`; may become canonical for shipment snapshot fields

---

## Global Diagnostic Pack

Run **before and after** every phase. Save output as `baseline-YYYY-MM-DD-phase-N.txt`.

```sql
-- 1) Flyway state
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

-- 2) Table inventory
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = 'automan_car_purchase'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- 3) purchases column count
SELECT COUNT(*) AS purchases_column_count
FROM information_schema.columns
WHERE table_schema = 'automan_car_purchase' AND table_name = 'purchases';

-- 4) Duplicate chassis (must not increase after migrations)
SELECT chassis, COUNT(*) AS cnt
FROM purchases
GROUP BY chassis
HAVING COUNT(*) > 1
ORDER BY cnt DESC
LIMIT 20;

-- 5) Orphan client_id on purchases
SELECT COUNT(*) AS orphan_client_ids
FROM purchases p
LEFT JOIN clients c ON p.client_id = c.id
WHERE p.client_id IS NOT NULL AND c.id IS NULL;

-- 6) Shipping without purchase
SELECT COUNT(*) AS shipping_without_purchase
FROM shipping_history sh
LEFT JOIN purchases p ON p.chassis = sh.chassis
WHERE p.id IS NULL;

-- 7) Invoice lines without purchase
SELECT COUNT(*) AS invoice_lines_without_purchase
FROM invoice_history_line il
LEFT JOIN purchases p ON p.chassis = il.chassis
WHERE p.id IS NULL;

-- 8) NULL workflow_status on active rows
SELECT COUNT(*) AS null_workflow_status
FROM purchases
WHERE workflow_status IS NULL;

-- 9) INVOICE_ISSUED without invoice_number
SELECT COUNT(*) AS invoice_issued_missing_number
FROM events
WHERE event_type = 'INVOICE_ISSUED' AND (invoice_number IS NULL OR TRIM(invoice_number) = '');

-- 10) Ledger events with invalid client (should be 0 — FK)
SELECT COUNT(*) AS orphan_events
FROM events e
LEFT JOIN clients c ON e.client_id = c.id
WHERE c.id IS NULL;

-- 11) Booking-eligible pool size (critical metric)
SELECT COUNT(*) AS booking_eligible_count
FROM purchases p
WHERE (p.booking_requested IS NULL OR p.booking_requested = 0)
  AND UPPER(TRIM(COALESCE(p.rixo_confirmed, ''))) IN ('TRUE', '1');

-- 12) Invoice-pending pool
SELECT COUNT(*) AS invoice_pending_count
FROM purchases p
WHERE (p.invoice_confirmed IS NULL OR p.invoice_confirmed = 0)
  AND (p.booking_requested IS NULL OR p.booking_requested = 0);
```

**Gate:** Counts in (4)–(10) must not **worsen** vs baseline. (11)–(12) must match baseline ± documented test changes only.

---

## Quick API smoke checklist

Run on staging after each phase (valid session/JWT required).

```text
GET  /purchases/page-search?q=TOYOTA&page=0&size=20
GET  /purchases/countries
GET  /purchases/filtered-chassis?country=...&polPort=...
GET  /purchases/costs-by-chassis/{chassis}
GET  /purchases/purchase/{id}
GET  /purchases/{id}/change-history
GET  /car-brand-mapping/chassis/{chassis}/match
GET  /shipping-history/for-invoice/lines?clientName=...&vessel=...&shipmentDate=...
GET  /invoice-history
GET  /events/client/{clientId}
```

All must return **200** with the same JSON keys as pre-phase baseline.

---

## Phase 0 — Discovery (no schema change)

### Purpose

Measure duplication, workflow drift, and edit patterns. **Zero production risk** if read-only.

### Risk controls

- Read-only DB user or replica only
- No Flyway, no deploy

### Manual QA

None required (no app change). Optional: confirm staging mirrors prod.

### Diagnostic SQL — Phase 0 pack

```sql
-- A) Vehicle spec duplication vs chassis map (sample)
SELECT p.id, p.chassis, p.fuel AS purchase_fuel, m.fuel AS map_fuel
FROM purchases p
JOIN car_brand_mapping m ON p.chassis LIKE CONCAT(m.chassis, '%')
WHERE p.fuel IS NOT NULL AND m.fuel IS NOT NULL
  AND TRIM(p.fuel) <> '' AND TRIM(m.fuel) <> ''
  AND LOWER(TRIM(SUBSTRING_INDEX(m.fuel, ';', 1))) <> LOWER(TRIM(p.fuel))
LIMIT 50;

-- B) % purchases with any vehicle spec populated
SELECT
  COUNT(*) AS total,
  SUM(CASE WHEN fuel IS NOT NULL AND TRIM(fuel) <> '' THEN 1 ELSE 0 END) AS has_fuel,
  SUM(CASE WHEN grade IS NOT NULL AND TRIM(grade) <> '' THEN 1 ELSE 0 END) AS has_grade,
  SUM(CASE WHEN color IS NOT NULL AND TRIM(color) <> '' THEN 1 ELSE 0 END) AS has_color
FROM purchases;

-- C) Cost column population
SELECT
  SUM(CASE WHEN price IS NOT NULL AND TRIM(price) <> '' THEN 1 ELSE 0 END) AS has_price,
  SUM(CASE WHEN auction_fee IS NOT NULL AND TRIM(auction_fee) <> '' THEN 1 ELSE 0 END) AS has_auction_fee,
  SUM(CASE WHEN freight IS NOT NULL AND TRIM(freight) <> '' THEN 1 ELSE 0 END) AS has_freight,
  SUM(CASE WHEN total_price IS NOT NULL AND TRIM(total_price) <> '' THEN 1 ELSE 0 END) AS has_total_price
FROM purchases;

-- D) Workflow flag drift
SELECT workflow_status,
  SUM(CASE WHEN UPPER(TRIM(COALESCE(rixo_confirmed,''))) IN ('TRUE','1') THEN 1 ELSE 0 END) AS legacy_rixo_confirmed,
  SUM(CASE WHEN booking_requested = 1 THEN 1 ELSE 0 END) AS legacy_booking_requested,
  SUM(CASE WHEN invoice_confirmed = 1 THEN 1 ELSE 0 END) AS legacy_invoice_confirmed,
  COUNT(*) AS cnt
FROM purchases
GROUP BY workflow_status
ORDER BY cnt DESC;

-- E) workflow_status RIXO_CONFIRMED but legacy flag off
SELECT COUNT(*) AS workflow_legacy_mismatch
FROM purchases
WHERE workflow_status = 'RIXO_CONFIRMED'
  AND UPPER(TRIM(COALESCE(rixo_confirmed, ''))) NOT IN ('TRUE', '1');

-- F) Top edited fields (audit trail)
SELECT field_name, COUNT(*) AS edit_count
FROM purchase_change_history
GROUP BY field_name
ORDER BY edit_count DESC
LIMIT 30;

-- G) purchase_change_history volume
SELECT COUNT(*) AS total_audit_rows,
       COUNT(DISTINCT purchase_id) AS purchases_with_edits
FROM purchase_change_history;
```

### Go / no-go

| Go to Phase 1 when | Block if |
|--------------------|----------|
| Workflow drift report (D)(E) reviewed and mapping table agreed | >5% unexplained workflow/flag mismatch |
| Vehicle override % estimated (A)(B) | — |
| Top edited fields list (F) noted for later phases | — |
| [Field classification CSV](./purchases-field-classification.csv) skimmed; `reviewed` = APPROVED (solo owner) | You reject map + overrides model |

### Deliverables

- Completed solo review of field classification CSV
- `workflow_status` ↔ legacy flag mapping noted for Phase 1
- Phase 0 baseline file in `docs/diagnostics/`

---

## Phase 1 — Field registry + workflow alignment

### Expected schema

- `purchase_field_registry` (new, config/documentation)
- Optional: backfill `workflow_status` from legacy flags
- **No column drops**

### Risk controls (must implement)

| Control | Detail |
|---------|--------|
| Dual-read queries | Booking/invoice services accept eligibility via **both** `workflow_status` and legacy flags |
| Dual-write on workflow changes | Updates to `rixo_confirmed` / `booking_requested` / `invoice_confirmed` also set `workflow_status` + `workflow_status_updated_at` |
| Registry is non-blocking | Runtime must not depend on registry until later phases |
| Backfill idempotent | Safe to re-run; log unmappable rows |

### Manual QA — Phase 1

**Auth**

- [ ] Login as ADMIN and VIEWER

**Purchases list**

- [ ] Page search: chassis, brand, client, supplier (`GET /purchases/page-search?q=…`)
- [ ] Sort list by date/chassis

**Rixo flow**

- [ ] `GET /purchases/distinct-purchase-dates` returns dates with pending Rixo
- [ ] Create Rixo PDF (`POST /purchases/rixo-pdf`) for a test date
- [ ] Rixo History → Confirm selected (`POST /rixo-history/confirm-selected`)
- [ ] After confirm: purchase shows Rixo confirmed in UI

**Booking flow (critical)**

- [ ] `GET /purchases/countries` — non-empty
- [ ] Pick country → `GET /purchases/pols-by-country?country=…`
- [ ] `GET /purchases/filtered-chassis?country=…&polPort=…` — includes known test chassis
- [ ] `GET /purchases/filtered-purchases?country=…&polPort=…` — same set
- [ ] Car Booking: chassis prefix search (`GET /purchases/search-chassis`)
- [ ] Calculate C&F/FOB → `PUT /purchases/save-costs` or `save-fob-costs`
- [ ] Booking Requested (`POST /purchases/booking-requested`) — cars leave eligible pool
- [ ] `shipping_history` row created (`POST /shipping-history/batch`)

**Invoice flow (critical)**

- [ ] Invoice generator filters (`GET /shipping-history/for-invoice/*`)
- [ ] `POST /purchases/invoice/save` or confirm saves `invoice_history`
- [ ] Purchases marked `invoice_confirmed`
- [ ] Client ledger shows `INVOICE_ISSUED` with correct amount
- [ ] Invoice History list + PDF (`GET /invoice-history/{invoiceNumber}/pdf`)

**Chassis map**

- [ ] `GET /car-brand-mapping/chassis/{chassis}/match` returns expected specs
- [ ] Add Purchase: enter chassis → fields auto-fill
- [ ] Edit Purchase: chassis-first load still works

**Audit**

- [ ] Edit one field → `GET /purchases/{id}/change-history` records change

### Diagnostic SQL — Phase 1

```sql
SELECT COUNT(*) AS registry_rows FROM purchase_field_registry;

SELECT COUNT(*) AS should_be_zero
FROM purchases
WHERE workflow_status IS NULL
  AND (
    UPPER(TRIM(COALESCE(rixo_confirmed,''))) IN ('TRUE','1')
    OR booking_requested = 1
    OR invoice_confirmed = 1
    OR UPPER(TRIM(COALESCE(rixo_requested,''))) IN ('TRUE','1')
  );

SELECT workflow_status, COUNT(*) FROM purchases GROUP BY workflow_status;

SELECT COUNT(*) AS booking_eligible_count
FROM purchases p
WHERE (p.booking_requested IS NULL OR p.booking_requested = 0)
  AND UPPER(TRIM(COALESCE(p.rixo_confirmed, ''))) IN ('TRUE', '1');

SELECT COUNT(*) AS eligible_via_workflow
FROM purchases
WHERE workflow_status = 'RIXO_CONFIRMED'
  AND (booking_requested IS NULL OR booking_requested = 0);
```

### Go / no-go

| Go | No-go |
|----|-------|
| All manual QA green | Booking/invoice list empty incorrectly |
| `should_be_zero` = 0 | Workflow mismatch > baseline |
| Booking eligible count = baseline | Rixo confirm does not advance workflow |

### Rollback

- Revert app deploy (registry unused at runtime)
- Drop `purchase_field_registry` only if empty

---

## Phase 2 — Cost lines normalization

### Expected schema

- `purchase_cost_lines (purchase_id, cost_code, amount, sort_order)`
- Dual-write: old VARCHAR cols **and** cost lines on every save/import

### Risk controls (must implement)

| Control | Detail |
|---------|--------|
| Dual-write | `save-costs`, `save-fob-costs`, `PUT /purchases/{id}`, CSV import write both |
| Read adapter | `GET /purchases/costs-by-chassis/{chassis}` built from lines with column fallback |
| Keep `total_price` column | Until list sort alternative exists |
| Cost codes | 1:1 map in `purchase_field_registry` (see CSV) |
| No column drops | This phase |

### Manual QA — Phase 2

- [ ] `GET /purchases/costs-by-chassis/{chassis}` — all fee fields present
- [ ] Edit costs on purchase form → save → reload → values match
- [ ] C&F Calculate → totals match pre-phase screenshot
- [ ] FOB path (`save-fob-costs`) same check
- [ ] `shipping_history.amount` correct for test chassis
- [ ] `POST /purchases/import` with standard CSV — no errors
- [ ] Spot-check 3 imported rows vs file
- [ ] Invoice line amounts + ledger `INVOICE_ISSUED` amount unchanged
- [ ] Purchase list price/total columns display correctly

### Diagnostic SQL — Phase 2

```sql
SELECT COUNT(*) AS cost_line_rows,
       COUNT(DISTINCT purchase_id) AS purchases_with_lines
FROM purchase_cost_lines;

SELECT COUNT(*) AS dual_write_gaps_price
FROM purchases p
WHERE p.price IS NOT NULL AND TRIM(p.price) <> ''
  AND NOT EXISTS (
    SELECT 1 FROM purchase_cost_lines c
    WHERE c.purchase_id = p.id AND c.cost_code = 'PRICE'
  );

SELECT cost_code, COUNT(*) AS line_count
FROM purchase_cost_lines
GROUP BY cost_code
ORDER BY line_count DESC;

SELECT COUNT(*) AS zero_amount_shipping
FROM shipping_history
WHERE amount = 0;
```

### Go / no-go

| Go to Phase 3 | Block |
|---------------|-------|
| `dual_write_gaps_price` = 0 for rows touched since deploy | Gaps on import or save-costs |
| C&F + invoice QA pass | `costs-by-chassis` missing fields |

### Rollback

- Read adapter: columns only
- Stop writing `purchase_cost_lines`

---

## Phase 3 — Vehicle spec overrides

### Expected schema

- `purchase_vehicle_overrides (purchase_id PK, overrides JSON)`
- Read: merge `car_brand_mapping` prefix match + overrides
- Dual-write: legacy spec columns during soak

### Risk controls (must implement)

| Control | Detail |
|---------|--------|
| Merge on read | API returns same flat JSON keys (`fuel`, `grade`, etc.) |
| Override on write | Only deltas vs chassis map stored in JSON |
| Dual-write | Legacy columns populated during soak |
| History recreate | sessionStorage payload still full flat object |

### Manual QA — Phase 3

- [ ] New purchase: chassis → specs auto-fill
- [ ] Change fuel manually → save → reload → custom value kept
- [ ] Map defaults only → overrides minimal/empty
- [ ] Brand → car name → chassis cascade works
- [ ] Old purchase opens with all specs visible
- [ ] Edit spec → audit `field_name` correct
- [ ] Rixo PDF correct vehicle attributes
- [ ] Rixo / Invoice / Shipping history recreate prefills form

### Diagnostic SQL — Phase 3

```sql
SELECT COUNT(*) AS override_rows
FROM purchase_vehicle_overrides
WHERE overrides IS NOT NULL AND JSON_LENGTH(overrides) > 0;

SELECT p.id, p.chassis
FROM purchases p
LEFT JOIN car_brand_mapping m ON p.chassis LIKE CONCAT(m.chassis, '%')
WHERE m.id IS NULL
  AND (p.fuel IS NOT NULL OR p.car_name IS NOT NULL)
LIMIT 20;

SELECT COUNT(*) AS null_fuel_but_has_override
FROM purchases p
JOIN purchase_vehicle_overrides o ON o.purchase_id = p.id
WHERE JSON_EXTRACT(o.overrides, '$.fuel') IS NOT NULL
  AND (p.fuel IS NULL OR TRIM(p.fuel) = '');
```

### Go / no-go

| Go to Phase 4 | Block |
|---------------|-------|
| All chassis/form QA pass | Blank edit forms |
| Recreate flows pass | Rixo PDF wrong specs |
| `null_fuel_but_has_override` = 0 | Dual-write broken |

### Rollback

- Read columns only; stop writing overrides

---

## Phase 4 — Extended attributes, shipping dedup, column drops

### Expected schema

- `purchases.extended_attributes` JSON for cold fields
- Shipment snapshot canonical on `shipping_history` (join by chassis)
- Column drops in **separate** gated sub-releases

### Risk controls (must implement)

| Control | Detail |
|---------|--------|
| Drop columns last | Own release per column group |
| Optional `v_purchases_legacy` view | Reports during transition |
| Invoice filters | Use `shipping_history` if purchase shipment cols removed |
| 30-day soak | After read-switch before each DROP |
| Never drop without audit | `chassis`, `client_id`, `booking_id`, `country`, `pol`, `workflow_status` |

### Manual QA — Phase 4

- [ ] `negotiate`, `shaken`, `is_package_mode` persist
- [ ] `car_pictures`, `venue_id`, `number_cut` on form
- [ ] Invoice filters via shipping history
- [ ] **Full Phase 1 regression** repeated
- [ ] Backend starts with `ddl-auto: validate` (prod profile)
- [ ] `./scripts/verify-schema.sh` passes (Docker)

### Diagnostic SQL — Phase 4

```sql
SELECT COUNT(*) AS rows_with_extended
FROM purchases
WHERE extended_attributes IS NOT NULL
  AND JSON_LENGTH(extended_attributes) > 0;

SELECT COUNT(*) AS booked_without_shipping
FROM purchases p
WHERE p.booking_requested = 1
  AND NOT EXISTS (SELECT 1 FROM shipping_history sh WHERE sh.chassis = p.chassis);

-- Pre-drop: column still written in last 90 days?
SELECT COUNT(*) AS recent_nonempty
FROM purchases
WHERE fuel IS NOT NULL AND TRIM(fuel) <> ''
  AND updated_at > DATE_SUB(NOW(), INTERVAL 90 DAY);

SELECT COUNT(*) AS purchases_column_count
FROM information_schema.columns
WHERE table_schema = 'automan_car_purchase' AND table_name = 'purchases';
```

### Go / no-go for column drops

| Drop allowed | Never without explicit sign-off |
|--------------|--------------------------------|
| Dual-write off ≥30 days | `chassis`, `client_id`, `booking_id` |
| Full regression pass | `country`, `pol`, `workflow_status` |
| `recent_nonempty` = 0 for dropped field | `total_price` until sort alternative |
| Prod snapshot restore test passed | `events` / `invoice_history` |

### Rollback

- Re-add nullable column via Flyway; backfill from new structures
- Restore DB snapshot if drop already applied

---

## Release sign-off template

```text
Phase: ___
Environment: staging / prod
Date: ___
DB backup ID: ___

Global diagnostic pack: PASS / FAIL (attach file)
Phase diagnostic SQL: PASS / FAIL
Manual QA: ___ / ___ steps passed
Dual-write verified: YES / NO / N/A
Read-switch active: YES / NO
Known regressions: none / listed

Decision: GO Phase ___ / HOLD / ROLLBACK

Sign-off: _______________
```

---

## Risk summary

| Risk | Mitigation |
|------|------------|
| Booking lists empty | Dual-read workflow; migrate queries before dropping `rixo_confirmed` |
| Invoice wrong cars | Shipping history parity; invoice filter QA |
| Ledger corruption | Do not change `events` / invoice posting |
| Forms blank | Merge map + overrides on read; dual-write during soak |
| CSV import fails | Dual-write until ops approves new template |
| C&F wrong totals | Cost lines dual-write; verify `shipping_history.amount` |
| Big-bang failure | One phase per release; drops separate |
| No rollback | Backup + read-switch config + no drops with read-switch |

---

## Target end state

| Structure | Role |
|-----------|------|
| `purchases` (~25 core columns) | Keys, search, filters, workflow, denorm search fields |
| `purchase_field_registry` | Schema metadata (labels, types, source) |
| `purchase_cost_lines` | All fee/amount fields |
| `purchase_vehicle_overrides` | Per-car spec deltas vs chassis map |
| `purchases.extended_attributes` | Cold flags and misc JSON |
| `car_brand_mapping` | Default vehicle specs (unchanged role) |
