# Schema Consolidation — Scope Freeze

**Effective:** 2026-06-26  
**Status:** LIFTED (2026-06-29) — Phase 4 pre-go-live drops complete; cost/vehicle column drops deferred  
**Owner:** Solo project — you are product, ops, backend, and DBA  
**Related:** [phase gates](./schema-consolidation-phase-gates.md) · [field classification](./purchases-field-classification.csv)

---

## Agreement (self-imposed)

Until **Phase 1** (`purchase_field_registry` + workflow alignment) is deployed and you complete the phase gate checklist:

1. **No new columns** on `purchases` (no Flyway `ALTER TABLE purchases ADD …`).
2. **No new indexes** on `purchases` unless you document why in this file.
3. **Bug fixes** on existing columns are allowed.
4. **Exceptions:** note them in the exception log below — no second approver needed.

### Rationale

- Avoid schema drift while the consolidation model is being introduced.
- Keep `purchases-field-classification.csv` stable for Phase 1 registry seed.
- Last column before freeze: `negotiate` (V40). Planned for Phase 4 `extended_attributes`.

### Allowed during freeze

| Allowed | Not allowed |
|---------|-------------|
| Phase 0 diagnostic SQL (read-only) | `V41+` adding `purchases.*` columns |
| New tables (`purchase_field_registry`, etc.) | Renaming/dropping `purchases` columns |
| Fixes to existing column behavior | Hibernate-only schema changes without Flyway |
| Other tables (`car_brand_mapping`, `master_menu`, …) as usual | |

---

## Field classification (solo review)

Classifications live in [purchases-field-classification.csv](./purchases-field-classification.csv).

| `review_decision` | Meaning |
|-------------------|---------|
| **KEEP** | Stays a real column (keys, search, filters) |
| **MIGRATE** | Moves per `classification` column (cost lines, overrides, JSON, etc.) |
| **N/A** | Legacy/dropped — do not restore |

**Solo review:** Skim the CSV once. Change any row where you disagree — set `review_notes` and update `review_decision`. Google Sheets is optional; editing the CSV in the repo is fine.

---

## Owner sign-off

| Check | Done |
|-------|------|
| I will not add new `purchases` columns until Phase 1 prod gate passes | ☑ |
| I reviewed `purchases-field-classification.csv` (KEEP vs MIGRATE) | ☑ |
| I understand booking / invoice / ledger flows must pass QA each phase | ☑ |

**Owner:** owner **Date:** 2026-06-26

---

## Exception log

| Date | Change | Reason |
|------|--------|--------|
| | | |

---

## Lift freeze when

- [ ] Phase 1 deploy complete (prod or your only environment)
- [ ] Phase 1 checklist in [phase gates](./schema-consolidation-phase-gates.md) filled
- [ ] Update **Status → LIFTED** with date below

**Lifted on:** 2026-06-29

After lift: any new `purchases` column still needs a CSV row + Flyway + phase assignment.
