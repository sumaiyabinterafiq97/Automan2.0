# Schema Consolidation — Zero-Risk Plan for Remaining Column Drops

**Created:** 2026-06-29  
**Current state:** Flyway **V52** · `purchases` **55 columns** · read-switches **complete** for cost lines, vehicle overrides, extended JSON, shipping  
**Target:** `purchases` **~25 core columns** (per [phase gates](./schema-consolidation-phase-gates.md))  
**Related:** [field matrix](./purchases-field-classification.csv) · [client handover](./client-handover-schema-consolidation.md) · **[pre-handoff runbook](./schema-consolidation-pre-handoff-column-drop-runbook.md)** (step-by-step V53–V55)

---

## Core principle: zero risk to the client

**“Zero risk” does not mean “drop all columns in one migration.”** It means:

1. **Client-visible behavior never changes** — same API JSON keys, same screens, same CSV headers.
2. **Each drop is reversible** — DB backup + Flyway rollback script before every migration.
3. **One drop group per release** — if something breaks, you know exactly what caused it.
4. **No drop until gates pass** — automated tests + diagnostic SQL + owner manual QA sign-off.

### Execution tracks

| Track | When | Document |
|-------|------|----------|
| **Pre-handoff (full drop to ~25 cols)** | Before client handoff | [Pre-handoff runbook](./schema-consolidation-pre-handoff-column-drop-runbook.md) |
| **Post-handoff (maintenance window)** | After client is stable 1–2 weeks | Same runbook; longer gap between releases |

Pre-handoff sequence (3 releases, ~3–5 working days with QA):

| Release | Flyway | ~Columns after |
|---------|--------|----------------|
| 1 — Vehicle spec | V53 | ~41 |
| 2 — Workflow flags | V54 | ~37 |
| 3 — Cost lines | V55 | ~22–25 |

---

## Legacy note (superseded timeline)

The table below described a post-handoff cadence. For **pre-handoff delivery**, follow the runbook instead.

## What is already done (do not redo)

| Area | Canonical store | Legacy columns on `purchases` |
|------|-----------------|-------------------------------|
| Cold / misc fields | `extended_attributes` JSON | **Dropped** (V51) |
| Shipping snapshot | `shipping_history` | **Dropped** (V52) |
| Vehicle specs | `purchase_vehicle_overrides` + `car_brand_mapping` | Still present (dual-write) |
| Costs / fees | `purchase_cost_lines` | Still present (dual-write) |
| Workflow | `workflow_status` | Legacy flags still present |

Read adapters in `PurchaseService` already merge canonical stores into flat API responses.

---

## What remains (three gated drop groups)

### Group A — Vehicle spec columns (~14 cols) · **Lowest risk · do first**

**Flyway:** V53 (proposed)  
**Remove from `purchases`:**

`car_model_year`, `shipment_size`, `grade`, `rank`, `color`, `fuel`, `seat`, `door`, `distance`, `CC`, `shift`, `WD`, `drive_type`

**Keep canonical:**

- Baseline: `car_brand_mapping` (by chassis / brand)
- Overrides: `purchase_vehicle_overrides.overrides` JSON
- Service: `PurchaseVehicleOverrideService` (already wired)

**Never drop:** `brand`, `car_name` (search/list performance — classified KEEP)

---

### Group B — Workflow legacy flags (~4 cols) · **Medium risk · do second**

**Flyway:** V54 (proposed)  
**Remove from `purchases`:**

`rixo_requested`, `rixo_confirmed`, `booking_requested`, `invoice_confirmed`

**Keep canonical:** `workflow_status`, `workflow_status_updated_at`

**Pre-drop requirement:** Every repository query that filters on legacy flags must use `PurchaseWorkflowService` JPQL/SQL helpers (partially done in Phase 1). **Grep audit mandatory.**

**Never drop:** `workflow_status`

---

### Group C — Cost line columns (~15 cols) · **Highest risk · do last**

**Flyway:** V55 (proposed)  
**Remove from `purchases`:**

`price`, `auction_fee`, `auction_penalty_fee`, `recycle_fee`, `road_tax`, `tax_total`, `shipment_charges`, `freight`, `storage_charges`, `misc_charges`, `inspection_fee`, `commission`, `rixo_price`, `repair_charges`, `profit`

**Keep canonical:** `purchase_cost_lines` (by `cost_code`)  
**Service:** `PurchaseCostLineService` (already wired)

**Never drop:** `total_price` (list sort/display until alternative indexed)

---

## Zero-risk execution template (repeat per group)

Use this checklist for **each** group (A, B, C). **Do not start the next group until the previous sign-off file exists.**

### Phase 0 — Freeze (no code change)

- [ ] Client go-live complete **or** explicit decision to drop pre-go-live
- [ ] Full DB backup; record backup ID in sign-off file
- [ ] `./scripts/verify-schema.sh` PASS
- [ ] Baseline diagnostics saved to `docs/diagnostics/baseline-YYYY-MM-DD-drop-<group>.txt`

### Phase 1 — Audit (read-only)

- [ ] Grep backend + frontend for direct reads/writes of columns in this drop group
- [ ] Write `docs/diagnostics/phase5-drop-<group>-audit.txt` (file paths + required code changes)
- [ ] **Stop if audit finds unmapped native SQL** on columns to drop

### Phase 2 — Gate SQL (read-only on staging/prod copy)

```sql
-- Vehicle (Group A): every purchase with non-empty spec column has override row or map baseline
SELECT COUNT(*) AS vehicle_dual_write_gaps
FROM purchases p
WHERE (p.fuel IS NOT NULL AND TRIM(p.fuel) <> '')
  AND NOT EXISTS (SELECT 1 FROM purchase_vehicle_overrides o WHERE o.purchase_id = p.id);

-- Cost (Group C): every purchase with price has cost lines
SELECT COUNT(*) AS cost_dual_write_gaps
FROM purchases p
WHERE p.price IS NOT NULL AND TRIM(p.price) <> ''
  AND NOT EXISTS (SELECT 1 FROM purchase_cost_lines c WHERE c.purchase_id = p.id);

SELECT COUNT(*) AS purchases_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'purchases';
```

**Go rule:** `dual_write_gaps = 0` for the group being dropped (or documented exceptions with manual backfill).

### Phase 3 — Implement (one Flyway migration)

- [ ] Entity: dropped fields → `@Transient` (same pattern as V51/V52)
- [ ] Service: stop dual-write to dropped columns; keep read adapter
- [ ] `finalizePurchaseWrite` / update paths: merge transient fields before sync (pattern from extended/shipping fix)
- [ ] Integration tests for GET/list/save + affected flows
- [ ] Flyway `DROP COLUMN` only — no other schema changes in same migration

### Phase 4 — Verify

- [ ] `./gradlew test` PASS
- [ ] Deploy to **local Docker**, then **staging** if available
- [ ] Manual QA checklist (group-specific — see below)
- [ ] `./scripts/verify-schema.sh` PASS
- [ ] Write `docs/diagnostics/phase5-drop-<group>-signoff.txt`

### Phase 5 — Rollback plan (document before deploy)

| Scenario | Action |
|----------|--------|
| Tests fail before deploy | Do not deploy; fix code |
| QA fails after deploy | Restore DB snapshot **or** run prepared rollback Flyway (re-add nullable columns + backfill from canonical store) |
| Client reports bug | Redeploy previous backend JAR + restore DB if columns already dropped |

---

## Group-specific manual QA (minimum)

### After Group A (vehicle)

- [ ] Purchase edit: color, fuel, grade, seat, door persist and reload
- [ ] Rixo PDF / purchase PDF shows correct specs
- [ ] CSV import still fills vehicle fields
- [ ] Chassis with only map baseline (no override) still shows map defaults

### After Group B (workflow)

- [ ] Car booking search lists Rixo-confirmed, booking-not-requested chassis
- [ ] Set booking requested → workflow status updates
- [ ] Invoice confirmed / Sold flag behavior unchanged
- [ ] **Full Phase 1 regression** from phase gates doc

### After Group C (cost)

- [ ] C&F calculator totals match pre-drop baseline for 3 known chassis
- [ ] CSV import (`docs/samples/phase2-import-test-3rows.csv`)
- [ ] Purchase list still shows `totalPrice`
- [ ] Package mode vs line-item mode totals
- [ ] `dual_write_gaps_price = 0` on staging

---

## Optional zero-risk comfort layer (no drops)

If you want thinner reporting **without** dropping anything:

**`v_purchases_legacy` view** — SQL view joining `purchases` + overrides + cost lines + extended JSON + shipping_history so reports use one SELECT. **Zero application risk**; add via Flyway anytime.

This does not reduce column count but reduces analyst confusion.

---

## What never to do (project risk)

| Action | Why |
|--------|-----|
| Drop all ~30 columns in one migration | Cannot isolate failures; rollback is painful |
| Stop dual-write before drop gates pass | Data loss on canonical side |
| Drop `total_price` before list sort alternative | Breaks purchase list UX |
| Drop `chassis`, `client_id`, `booking_id`, `country`, `pol` | Breaks booking, invoice, ledger |
| Modify `events` / `invoice_history` tables | Out of scope; ledger corruption risk |
| Single JSON `metadata` column for everything | Loses indexes; breaks existing queries |
| Merge unrelated tables (e.g. `rixo_prices` into `purchases`) | Different lifecycles; not consolidation |

---

## Recommended timeline

**Pre-handoff:** follow [pre-handoff runbook](./schema-consolidation-pre-handoff-column-drop-runbook.md) (V53 → V54 → V55 with sign-off between each).

**Post-handoff maintenance (optional spacing):**

```
Week 1–2 after go-live → Monitor; diagnostic pack weekly
Then                   → Same runbook releases if not done pre-handoff
```

Never skip gates to save time.

## Agent prompts (copy when ready)

### Group A — vehicle (safest next step)

```text
Phase 5 Drop A — VEHICLE_OVERRIDE columns only (zero-risk plan).

Follow: docs/schema-consolidation-zero-risk-remaining-drops-plan.md
Playbook: docs/schema-consolidation-phase-gates.md

Steps: audit → gate SQL → Flyway V53 → @Transient fields → stop dual-write to dropped cols → tests → signoff.
Never drop: brand, car_name, chassis, client_id, booking_id, country, pol, workflow_status, total_price.
Do NOT touch cost columns or workflow flag columns in this release.
Stop if vehicle_dual_write_gaps > 0.
```

### Group B — workflow (only after A sign-off)

```text
Phase 5 Drop B — workflow legacy flags only.
Requires: phase5-drop-vehicle-signoff.txt complete.
Audit all JPQL/native SQL using rixo_requested, rixo_confirmed, booking_requested, invoice_confirmed.
Flyway V54. Never drop workflow_status.
```

### Group C — cost (only after B sign-off)

```text
Phase 5 Drop C — COST_LINE columns only.
Requires: phase5-drop-workflow-signoff.txt complete.
Gate: cost_dual_write_gaps = 0. Full C&F + CSV import QA.
Flyway V55. Never drop total_price.
```

---

## Handoff options (column count vs effort)

| Option | Releases | Final ~cols | Effort |
|--------|----------|-------------|--------|
| **Full pre-handoff (target)** | V53 + V54 + V55 | ~25 | 3–5 days + QA — [runbook](./schema-consolidation-pre-handoff-column-drop-runbook.md) |
| **Partial** | V53 only | ~41 | ~1 day — vehicle only |
| **Partial** | V53 + V54 | ~37 | ~2 days — defer cost to later |
| **Minimal** | None (stay V52) | 55 | 0 — dual-write safe but wide table |

**Pre-handoff target:** full runbook (V55 complete) + all phase5 sign-off files before client delivery.

## Success criteria (initiative complete)

- [ ] `purchases_column_count` ≤ 28 (core + `extended_attributes` + audit timestamps)
- [ ] All three sign-off files: vehicle, workflow, cost
- [ ] Client handover doc updated with final column count
- [ ] No open dual-write to dropped columns in code
- [ ] `./gradlew test` + owner manual QA PASS

---

## Sign-off file naming

| Group | Audit | Sign-off |
|-------|-------|----------|
| Vehicle | `docs/diagnostics/phase5-drop-vehicle-audit.txt` | `docs/diagnostics/phase5-drop-vehicle-signoff.txt` |
| Workflow | `docs/diagnostics/phase5-drop-workflow-audit.txt` | `docs/diagnostics/phase5-drop-workflow-signoff.txt` |
| Cost | `docs/diagnostics/phase5-drop-cost-audit.txt` | `docs/diagnostics/phase5-drop-cost-signoff.txt` |

**Rule:** No migration without audit file. No sign-off without manual QA checkbox completed by owner.
