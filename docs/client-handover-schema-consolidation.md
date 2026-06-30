# Automan 2.0 — Schema Consolidation Client Handover

**Date:** 2026-06-29  
**Status:** Pre-handoff column drops complete (V53–V55)

## What was delivered

| Phase | Feature | Storage |
|-------|---------|---------|
| 1 | Workflow + field registry | `workflow_status`, `purchase_field_registry` |
| 2 | Cost lines | `purchase_cost_lines` (**legacy cost cols dropped V55**) |
| 3 | Vehicle spec overrides | `purchase_vehicle_overrides` (**legacy spec cols dropped V53**) |
| 4 | Extended attributes | `purchases.extended_attributes` JSON (**legacy cols dropped V51**) |
| 4b/4c | Shipping snapshot | `shipping_history` vessel/date/bl_no (**legacy cols dropped V52**) |
| 5 | Workflow legacy flags dropped | `workflow_status` only (**V54**) |

**API contract:** Unchanged — clients still receive flat JSON keys (`fuel`, `price`, `auctionFee`, `rixoConfirmed`, `vessel`, etc.).

## Flyway migrations (through V55)

| Version | Description |
|---------|-------------|
| V48–V49 | extended_attributes column + backfill |
| V50 | shipping_history.bl_no + backfill |
| V51 | DROP extended JSON legacy columns (10 cols) |
| V52 | DROP shipping legacy columns (vessel, shippment_date, B/L_no) |
| V53 | DROP vehicle spec columns (14 cols) |
| V54 | DROP workflow legacy flags (4 cols) |
| V55 | DROP cost/fee columns (15 cols) |

**Current `purchases` column count:** 23 (target ~22–25 achieved)

## Deploy steps

1. **Backup database** (mandatory before each release — see `backup-pre-v55-YYYYMMDD.sql`)
2. Start stack: `./scripts/start-docker-stack.sh` (or prod equivalent)
3. Backend: `./scripts/rebuild-and-restart-backend.sh --prebuilt`
4. Frontend: `./scripts/build-and-deploy-frontend.sh` (no V55 frontend changes required)
5. Verify: `./scripts/verify-schema.sh`
6. Confirm Flyway V55:  
   `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;`

## Diagnostics (run after deploy)

```sql
SELECT COUNT(*) AS cost_dual_write_gaps
FROM purchases p
WHERE p.price IS NOT NULL AND TRIM(p.price) <> ''
  AND NOT EXISTS (SELECT 1 FROM purchase_cost_lines c WHERE c.purchase_id = p.id);
-- Expected: query invalid post-V55 (column dropped); use purchase_cost_lines row counts instead

SELECT COUNT(*) AS cost_line_rows FROM purchase_cost_lines;
SELECT COUNT(*) AS purchases_with_lines FROM (SELECT DISTINCT purchase_id FROM purchase_cost_lines) t;

SELECT COUNT(*) AS purchases_column_count FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'purchases';
```

Expected: `purchases_column_count = 23`, `cost_line_rows > 0`.

## Manual QA checklist (owner)

- [ ] Purchase list + edit: notes, shaken, negotiate, venueId, isPackageMode save and reload
- [ ] CSV import (`docs/samples/phase2-import-test-3rows.csv`)
- [ ] C&F calculate + save → cost lines + totalPrice correct
- [ ] Edit B/L, vessel, shipment date → shipping_history
- [ ] Invoice filter by client + vessel + date
- [ ] Shipping history list + recreate flow
- [ ] Booking search + Rixo flow
- [ ] Phase 5 cost regression (price, auctionFee, freight in API JSON)

## Phase 5 sign-offs

| Release | Flyway | Sign-off file |
|---------|--------|---------------|
| Vehicle spec | V53 | `docs/diagnostics/phase5-drop-vehicle-signoff.txt` |
| Workflow flags | V54 | `docs/diagnostics/phase5-drop-workflow-signoff.txt` |
| Cost lines | V55 | `docs/diagnostics/phase5-drop-cost-signoff.txt` |

## Rollback

1. Restore DB from pre-release snapshot (safest): `backup-pre-v55-YYYYMMDD.sql`
2. Or re-deploy previous backend JAR **and** run `docs/diagnostics/rollback-v55-template.sql` (re-add nullable columns + backfill from `purchase_cost_lines`)

Sign-off files: `docs/diagnostics/phase2-signoff.txt` through `phase5-drop-cost-signoff.txt`
