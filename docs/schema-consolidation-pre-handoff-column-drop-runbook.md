# Pre-Handoff Column Drop Runbook

**Purpose:** Step-by-step procedure to reduce `purchases` from **55 → ~25 columns** before client handoff, without changing API behavior or breaking flows.

**Current Flyway:** V52  
**Planned migrations:** V53 (vehicle) → V54 (workflow) → V55 (cost)  
**Related:** [phase gates](./schema-consolidation-phase-gates.md) · [field matrix](./purchases-field-classification.csv) · [safety principles](./schema-consolidation-zero-risk-remaining-drops-plan.md)

---

## Rules (read once)

1. **One drop group per release** — never combine V53 + V54 + V55 in a single deploy.
2. **STOP if any gate fails** — fix or backfill; do not deploy the Flyway migration.
3. **Full DB backup** before each Flyway version (V53, V54, V55).
4. **API JSON unchanged** — clients still receive `fuel`, `price`, `rixoConfirmed`, etc.
5. **Never drop:** `chassis`, `client_id`, `booking_id`, `country`, `pol`, `workflow_status`, `total_price`.
6. **Never modify:** `events`, `invoice_history`, `invoice_history_line`.
7. **Do not start Group B until Group A sign-off exists.** Same for C after B.

---

## Overview — three releases

| Release | Flyway | Columns dropped | Expected `purchases` col count | Risk |
|---------|--------|-----------------|-------------------------------|------|
| **1 — Vehicle** | V53 | 14 spec fields | ~41 | Low |
| **2 — Workflow** | V54 | 4 legacy flags | ~37 | Medium |
| **3 — Cost** | V55 | 15 fee fields | ~22–25 | High |

Each release: **Backup → Baseline → Audit → Gate SQL → Code + Flyway → Tests → Deploy → Manual QA → Sign-off**

Estimated calendar time (solo, with QA): **3–5 working days** (1–2 days per release).

---

# RELEASE 1 — Vehicle spec columns (V53)

## Columns to DROP

| DB column | API key | Canonical source |
|-----------|---------|------------------|
| `car_model_year` | `carModelYear` | `car_brand_mapping` + `purchase_vehicle_overrides` |
| `shipment_size` | `shipmentSize` / `vehicleType` | same |
| `grade` | `grade` | same |
| `rank` | `rank` | same |
| `color` | `color` | same |
| `fuel` | `fuel` | same |
| `seat` | `seat` | same |
| `door` | `door` | same |
| `distance` | `distance` | override only |
| `CC` | `cc` | same |
| `shift` | `shift` | same |
| `WD` | `wd` | same |
| `drive_type` | `driveType` | same |

**Do NOT drop:** `brand`, `car_name` (KEEP — list/search indexes).

---

## Step 1.1 — Backup and baseline

```bash
# Backup (example — adjust path/credentials)
docker exec automan_mysql_multiplatform mysqldump -uautoman_user -pautoman_password \
  automan_car_purchase > backup-pre-v53-$(date +%Y%m%d).sql

./scripts/verify-schema.sh
```

Save diagnostics:

```bash
docker exec automan_mysql_multiplatform mysql -uautoman_user -pautoman_password automan_car_purchase -e "
SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
SELECT COUNT(*) AS purchases_column_count FROM information_schema.columns
WHERE table_schema='automan_car_purchase' AND table_name='purchases';
SELECT COUNT(*) AS override_rows FROM purchase_vehicle_overrides;
" 2>&1 | grep -v Warning | tee docs/diagnostics/baseline-pre-v53.txt
```

**STOP if** Flyway latest ≠ V52 or `verify-schema.sh` fails.

---

## Step 1.2 — Audit (read-only)

Run greps (backend):

```bash
rg -n "car_model_year|carModelYear|shipment_size|shipmentSize|\.grade|\.fuel|\.color|\.seat|\.door|\.distance|\.cc|\.shift|\.wd|drive_type|driveType" \
  backend/src --glob '*.kt' > docs/diagnostics/phase5-drop-vehicle-grep.txt
```

Document in `docs/diagnostics/phase5-drop-vehicle-audit.txt`:

- Every file that **writes** these fields to `purchases` columns → must use `PurchaseVehicleOverrideService.syncFromPurchase` only after drop
- Every **native SQL** referencing dropped column names → must be removed or rewritten
- PDF/export paths (`PdfService`, etc.) → must read via API/adapters (merged `Purchase` object)

**STOP if** audit lists unmapped native SQL on spec columns with no fix plan.

---

## Step 1.3 — Gate SQL

```sql
-- Gap: non-empty fuel on purchase but no override row (should be 0 or backfill first)
SELECT COUNT(*) AS vehicle_dual_write_gaps
FROM purchases p
WHERE p.fuel IS NOT NULL AND TRIM(p.fuel) <> ''
  AND NOT EXISTS (SELECT 1 FROM purchase_vehicle_overrides o WHERE o.purchase_id = p.id);

-- Spot-check
SELECT p.id, p.chassis, p.fuel, o.overrides
FROM purchases p
LEFT JOIN purchase_vehicle_overrides o ON o.purchase_id = p.id
WHERE p.fuel IS NOT NULL AND TRIM(p.fuel) <> ''
LIMIT 10;
```

**If `vehicle_dual_write_gaps > 0`:** run backfill (Phase 3 V47 pattern) or trigger `syncFromPurchase` for affected IDs **before** drop. **STOP until gaps = 0.**

---

## Step 1.4 — Implement (agent or dev)

| Task | Detail |
|------|--------|
| Flyway **V53** | `ALTER TABLE purchases DROP COLUMN …` (14 columns listed above) |
| `Purchase.kt` | Spec fields → `@Transient` (same as V51 extended fields) |
| `PurchaseVehicleOverrideService` | JSON-only write; `applyForRead` unchanged |
| `PurchaseService` | Hydrate existing + merge transient before `finalizePurchaseWrite` on update/import |
| Tests | Extend pattern from `PurchaseVehicleOverrideIntegrationTest` + GET/list/save |
| Deploy | `./scripts/rebuild-and-restart-backend.sh --prebuilt` |

---

## Step 1.5 — Verify Release 1

```bash
cd backend && ./gradlew test
./scripts/verify-schema.sh
```

```sql
SELECT COUNT(*) AS purchases_column_count FROM information_schema.columns
WHERE table_schema='automan_car_purchase' AND table_name='purchases';
-- Expect ~41
SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
-- Expect 53
```

### Manual QA — Vehicle (all must pass)

- [ ] Open purchase → edit color, fuel, grade → save → reload → values correct
- [ ] Chassis with map baseline only (no override) → shows map defaults on GET
- [ ] Rixo / purchase PDF shows correct specs
- [ ] CSV import (`docs/samples/phase2-import-test-3rows.csv`) — vehicle fields populated
- [ ] Purchase list/search still works

Write **`docs/diagnostics/phase5-drop-vehicle-signoff.txt`** with backup ID, gate results, QA date.

**Do not start Release 2 until this file exists.**

---

# RELEASE 2 — Workflow legacy flags (V54)

**Prerequisite:** `phase5-drop-vehicle-signoff.txt` complete.

## Columns to DROP

| DB column | API key | Canonical source |
|-----------|---------|------------------|
| `rixo_requested` | `rixoRequested` | `workflow_status` (derived on read if needed) |
| `rixo_confirmed` | `rixoConfirmed` | same |
| `booking_requested` | `bookingRequested` | same |
| `invoice_confirmed` | `invoiceConfirmed` | same |

**Do NOT drop:** `workflow_status`, `workflow_status_updated_at`.

---

## Step 2.1 — Backup and baseline

Same as Step 1.1; save `backup-pre-v54-*.sql` and `baseline-pre-v54.txt`. Expect Flyway **V53**, ~41 columns.

---

## Step 2.2 — Audit

```bash
rg -n "rixo_requested|rixoRequested|rixo_confirmed|rixoConfirmed|booking_requested|bookingRequested|invoice_confirmed|invoiceConfirmed" \
  backend/src --glob '*.kt' > docs/diagnostics/phase5-drop-workflow-grep.txt
```

Confirm:

- Booking queries use `PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED` and `JPQL_RIXO_CONFIRMED_ELIGIBILITY` (not raw column-only filters)
- Invoice filter candidates use workflow or adapter paths
- Writes still update `workflow_status` via `PurchaseWorkflowService.recomputeByPurchaseId`

Document fixes in `docs/diagnostics/phase5-drop-workflow-audit.txt`.

**STOP if** any repository query filters **only** on legacy columns with no `workflow_status` fallback.

---

## Step 2.3 — Gate SQL

```sql
SELECT COUNT(*) AS null_workflow_status
FROM purchases WHERE workflow_status IS NULL;

SELECT workflow_status, COUNT(*) FROM purchases GROUP BY workflow_status;
```

**STOP if** `null_workflow_status > 0` — run V42 backfill pattern first.

---

## Step 2.4 — Implement

| Task | Detail |
|------|--------|
| Flyway **V54** | DROP 4 workflow legacy columns |
| `Purchase.kt` | Flags → `@Transient`; read adapter derives from `workflow_status` for API if needed |
| `PurchaseWorkflowService` | Single source for eligibility queries |
| Tests | Booking search integration tests (`PurchaseCountriesBookingIntegrationTest`, workflow tests) |

---

## Step 2.5 — Verify Release 2

Expect Flyway **V54**, ~37 columns.

### Manual QA — Workflow

- [ ] Car booking: country/POL/chassis search returns eligible cars
- [ ] Mark booking requested → status updates; car leaves booking pool
- [ ] Invoice confirmed / Sold behavior unchanged
- [ ] Full Phase 1 regression checklist ([phase gates](./schema-consolidation-phase-gates.md) Phase 1 section)

Sign-off: **`docs/diagnostics/phase5-drop-workflow-signoff.txt`**

---

# RELEASE 3 — Cost line columns (V55)

**Prerequisite:** `phase5-drop-workflow-signoff.txt` complete.

## Columns to DROP

`price`, `auction_fee`, `auction_penalty_fee`, `recycle_fee`, `road_tax`, `tax_total`, `shipment_charges`, `freight`, `storage_charges`, `misc_charges`, `inspection_fee`, `commission`, `rixo_price`, `repair_charges`, `profit`

**Do NOT drop:** `total_price`, `repair_company` (KEEP per matrix).

---

## Step 3.1 — Backup and baseline

`backup-pre-v55-*.sql`, `baseline-pre-v55.txt`. Expect Flyway **V54**.

---

## Step 3.2 — Audit

```bash
rg -n "\.price|auction_fee|auctionFee|freight|shipment_charges|shipmentCharges|rixo_price|profit" \
  backend/src --glob '*.kt' > docs/diagnostics/phase5-drop-cost-grep.txt
```

Focus: `PurchaseCostLineService`, C&F save paths, CSV import money columns, PDF totals, shipping PDF price column.

Document: `docs/diagnostics/phase5-drop-cost-audit.txt`

---

## Step 3.3 — Gate SQL

```sql
SELECT COUNT(*) AS cost_dual_write_gaps
FROM purchases p
WHERE p.price IS NOT NULL AND TRIM(p.price) <> ''
  AND NOT EXISTS (SELECT 1 FROM purchase_cost_lines c WHERE c.purchase_id = p.id);

SELECT COUNT(*) AS cost_line_rows FROM purchase_cost_lines;
SELECT COUNT(*) AS purchases_with_lines FROM (
  SELECT DISTINCT purchase_id FROM purchase_cost_lines
) t;
```

**STOP if** `cost_dual_write_gaps > 0` — run V45-style backfill or sync before drop.

---

## Step 3.4 — Implement

| Task | Detail |
|------|--------|
| Flyway **V55** | DROP 15 cost columns |
| `Purchase.kt` | Cost fields → `@Transient` (except `total_price`) |
| `PurchaseCostLineService` | Lines-only write; read adapter for GET/list |
| `PurchaseService` | Transient merge before `finalizePurchaseWrite` (import critical) |
| Tests | `PurchaseCostLineIntegrationTest` + C&F-related paths |

---

## Step 3.5 — Verify Release 3

Expect Flyway **V55**, ~22–25 columns.

### Manual QA — Cost

- [ ] C&F calculator: pick 3 known chassis; totals match screenshot/baseline from pre-V55
- [ ] CSV import sample file — prices and fees correct
- [ ] Purchase list shows `totalPrice`
- [ ] Package mode vs line-item mode
- [ ] Invoice / shipping PDF amounts sensible

Sign-off: **`docs/diagnostics/phase5-drop-cost-signoff.txt`**

---

# After all three releases — handoff package

## Final diagnostics

```sql
SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
SELECT COUNT(*) AS purchases_column_count FROM information_schema.columns
WHERE table_schema='automan_car_purchase' AND table_name='purchases';
SELECT COUNT(*) AS booked_without_shipping FROM purchases p
WHERE p.booking_requested = 1
  AND NOT EXISTS (SELECT 1 FROM shipping_history sh WHERE sh.chassis = p.chassis);
```

## Update handoff doc

Edit [`client-handover-schema-consolidation.md`](./client-handover-schema-consolidation.md):

- Final Flyway version (V55)
- Final column count
- List all sign-off files (phase2–phase5)

## Deploy to client environment

1. DB backup on client server  
2. Deploy backend (Flyway runs V53→V55 if fresh DB, or already at V55)  
3. `./scripts/build-and-deploy-frontend.sh`  
4. Run manual QA checklist from client handover doc  
5. Deliver sign-off files + this runbook

---

# Rollback (per release)

If QA fails **after** Flyway applied:

| Release | Fastest rollback |
|---------|------------------|
| Any | Restore `backup-pre-vXX-*.sql` + redeploy previous backend JAR |
| V53 only | Re-add 14 nullable spec columns + backfill from `purchase_vehicle_overrides` + map |
| V54 only | Re-add 4 boolean/varchar flags + backfill from `workflow_status` |
| V55 only | Re-add 15 cost columns + backfill from `purchase_cost_lines` |

Keep rollback SQL in `docs/diagnostics/rollback-v53-template.sql` (create when implementing each migration).

---

# Agent prompts (one release at a time)

### Release 1 only

```text
Execute Release 1 from docs/schema-consolidation-pre-handoff-column-drop-runbook.md (V53 vehicle spec drops only).

Steps: backup → baseline → audit → gate SQL → implement → test → deploy → signoff.
STOP if vehicle_dual_write_gaps > 0.
Do NOT touch workflow or cost columns.
Never drop: brand, car_name, chassis, client_id, booking_id, country, pol, workflow_status, total_price.
```

### Release 2 only (after vehicle sign-off)

```text
Execute Release 2 from docs/schema-consolidation-pre-handoff-column-drop-runbook.md (V54 workflow flag drops only).
Requires docs/diagnostics/phase5-drop-vehicle-signoff.txt.
```

### Release 3 only (after workflow sign-off)

```text
Execute Release 3 from docs/schema-consolidation-pre-handoff-column-drop-runbook.md (V55 cost column drops only).
Requires docs/diagnostics/phase5-drop-workflow-signoff.txt.
Gate: cost_dual_write_gaps = 0. Full C&F + CSV QA.
Never drop total_price.
```

---

# Quick reference — column count trajectory

```
V52 (now)     55 cols   extended + shipping already dropped
V53           ~41 cols  vehicle spec dropped
V54           ~37 cols  workflow flags dropped
V55           ~22–25    cost lines dropped (target met)
```

**Handoff-ready definition:** Flyway V55 + all three phase5 sign-off files + client handover doc updated + manual QA complete.
