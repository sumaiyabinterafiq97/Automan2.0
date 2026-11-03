# Production release readiness audit — V93 / V94

**Strategy under review:** Keep existing production Vercel frontend live → deploy V93 Railway backend → verify → deploy V94 backend → verify → deploy new Vercel frontend last.

**Purpose:** Determine whether that sequence is **technically safe** (read-only audit; **no deployment**).

**Commits:**

| Label | SHA |
|-------|-----|
| Pre-V93 baseline | `8b8f07b1252869c7c46245f07f96c97ae50c9b51` (parent of V93) |
| V93 | `ac72656206ad8ae3a62e0116d019edec714d6957` |
| V94 | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` |

**Repository HEAD (verified):** `2f03ad4…` on `master`

**Related docs:** `docs/staging-v93-v94-readiness-plan.md`, `docs/staging-v93-v94-phase2-architecture.md`, prior rollback audit (conversation).

---

## 1. Executive summary

| Question | Answer |
|----------|--------|
| Is **old frontend + V93 backend** technically plausible? | **COMPATIBLE WITH RISKS** — no controller contract changes; new JSON field `chassisKey`; internal chassis query paths change but aim for equivalence |
| Is **old frontend + V94 backend** technically plausible? | **COMPATIBLE WITH RISKS** — same as V93 plus hidden `auctionHouseNorm`; legacy auction-house behavior preserved for Rixo sync |
| Is the **proposed deployment sequence** sound in principle? | **Yes**, if preflight + backups + migration window are satisfied |
| Ready to deploy production **now**? | **No** — **BLOCKED — ADDITIONAL VERIFICATION REQUIRED** |

**Final verdict:** **READY AFTER SPECIFIED PRECONDITIONS** (equivalently: **BLOCKED** until preconditions are met — not **READY FOR PRODUCTION DEPLOYMENT** today).

Local tests passing (561 tests at V94) **do not** substitute for production Flyway duration, backup verification, or confirmation of **which commit is live on Vercel**.

---

## 2. Repository and commit verification

| Check | Result |
|-------|--------|
| Branch | `master` |
| HEAD | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` |
| V93 SHA | `ac72656206ad8ae3a62e0116d019edec714d6957` |
| V94 SHA | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` |
| V94 parent | V93 (**verified**) |
| Index | Empty (0 staged) |
| Working tree | **Not clean** — `.gradle` noise, modified `docs/railway-mysql-backup.md`, many untracked files (PDFs, tmp, staging docs) |

Commits **unchanged** from audit scope.

---

## 3. Existing frontend identification

### What the repo shows (**Verified**)

| Item | Evidence |
|------|----------|
| Production API routing | Root `vercel.json` and `public/automan/vercel.json` rewrite `/api/*` → `https://automan20-production.up.railway.app/api/*` |
| Frontend API base (Kotlin/JS) | `API_BASE_URL = "/api"` in `src/jsMain/kotlin/com/automan/purchase/Utils.kt` — browser calls same-origin `/api`, proxied to Railway |
| Primary client patterns | `ApiClient` uses `JSON.parse` → dynamic objects; `ApiService` uses kotlinx `Json { ignoreUnknownKeys = true }` with `ApiPurchase` DTO |
| Build | `./gradlew jsBrowserProductionWebpack prepareVercelPublic` → `public/` (`vercel.json`) |

### What is **NOT VERIFIED**

| Item | Why it matters |
|------|----------------|
| **Exact Git commit deployed on production Vercel** | Old frontend compatibility assumes deployed bundle matches a known SHA; repo `master` may be ahead of Vercel |
| **Whether production uses root vs `public/automan` project** | Both rewrite files point to same Railway host |
| **Age of deployed JS vs backend** | Client may lack recent UI fixes but still hit same REST paths |

**Assumption for this audit:** “Existing production frontend” = last deployed Vercel bundle built from Automan2.0 **before** this release, using relative `/api` and typical purchase flows. Treat as **NOT VERIFIED** until compared to Vercel deployment history.

---

## 4. Old frontend → V93 compatibility

### Controller / route layer (**Verified**)

`git diff 8b8f07b..ac726562 -- backend/.../controller/PurchaseController.kt` → **0 lines changed**.

No new HTTP paths, methods, or controller-level validation changes in V93.

### Entity JSON (**Verified**)

At V93 (`ac726562`), `Purchase.kt` adds:

- `chassisKey` — **not** `@JsonIgnore` → **new property in JSON** for all entity-backed responses (`PurchaseController`, `PurchasePageResponse.content`, create/update responses).
- `@PrePersist` / `@PreUpdate` maintains `chassisKey` from `chassis`.

At pre-V93, `chassisKey` **absent** from entity and responses.

### Frontend parsing (**Verified**)

| Client | Extra `chassisKey` |
|--------|-------------------|
| `ApiService` / `ApiPurchase` | **Ignored** (`ignoreUnknownKeys = true`; field not in DTO) |
| `ApiClient` + dynamic JS | **Ignored** (extra properties on dynamic objects) |
| Strict JSON Schema clients | **NOT VERIFIED** — none in repo |

**Risk:** Low for in-repo frontend; any external integration with `additionalProperties: false` would be **INCOMPATIBLE** (**NOT VERIFIED**).

### Behavioral / query layer (**Verified with risks**)

V93 changes **internal** chassis resolution (services + `PurchaseRepository`):

- Equality/prefix/token queries use `chassis_key` column instead of `LOWER(TRIM(chassis))` expressions (see `PurchaseRepository.kt` diff).
- `PurchaseService`, `PurchaseChassisLookupService`, Rixo history overlap, dashboard, car search, etc. refactored to indexed paths.

**Evidence of intended equivalence:** MySQL/JPA tests (`PurchaseChassisKeyV93MigrationMysqlTest`, `ChassisQueryCountMysqlIntegrationTest`, `PurchaseChassisLookupServiceTest`, etc.) — local, not production.

**Risk:** If `chassis_key` stale vs `chassis` (bypassing JPA hooks), indexed lookups could diverge from legacy SQL. Migration backfill + hooks mitigate for normal paths.

**Auction-house / supplier:** V93 does **not** change `findByAuctionHouseIgnoreCaseTrim` or filter endpoints.

### V93 migration vs old frontend

Frontend does **not** call Flyway. During V93 migration, backend may be **down** until Flyway + validate complete → frontend sees 502/503 — **operational**, not contract break.

**Status:** **COMPATIBLE WITH RISKS**

---

## 5. Old frontend → V94 compatibility

### Controller layer (**Verified**)

`git diff ac726562..2f03ad4 -- backend/.../controller/` → **0 lines changed**.

### Entity JSON (**Verified**)

V94 adds:

- `auctionHouseNorm` with **`@JsonIgnore`** → **not** exposed in JSON.
- Lifecycle + `PurchaseService` / `withNormalizedAuctionHouseNorm()` maintain column.

`PurchaseRixoPriceSyncService` still uses **`findByAuctionHouseIgnoreCaseTrim`** (**Verified** — no change in V94 diff on that file).

### Repository (**Verified**)

Adds `findByAuctionHouseNorm` — **no production controller caller** in repo; existing filter/search JPQL on `auctionHouse` unchanged.

### API behavior

- **Request bodies:** Unchanged shapes; clients may send unknown fields — `@JsonIgnoreProperties(ignoreUnknown = true)` on `Purchase`.
- **Responses:** Same as V93 plus still **no** `auctionHouseNorm`; still includes **`chassisKey`**.

**Status:** **COMPATIBLE WITH RISKS** (same chassisKey + query equivalence caveats as V93).

---

## 6. New frontend compatibility (repo at HEAD / post-release)

If “new frontend” = future Vercel deploy from `master` at or after `2f03ad4`:

| Backend | Status | Evidence |
|---------|--------|----------|
| V93 | **COMPATIBLE WITH RISKS** | Same as old frontend; new UI may display `chassisKey` if bound to full Purchase JSON |
| V94 | **COMPATIBLE WITH RISKS** | Same |

If new frontend is built from **same** Kotlin/JS clients as today, behavior matches old frontend for API parsing.

---

## 7. API contract comparison (summary)

| Area | Pre-V93 → V93 | V93 → V94 |
|------|---------------|-----------|
| HTTP routes / methods | **Unchanged** (controllers) | **Unchanged** |
| Purchase response shape | **+ `chassisKey`** (optional for clients) | **+ hidden `auctionHouseNorm` (server only)** |
| Purchase request validation | **Unchanged** at controller | **Unchanged** |
| Auth | **Unchanged** in diff scope | **Unchanged** |
| Pagination / sort endpoints | **Unchanged** controllers; V93 service optimizations | **Unchanged** |
| Chassis search / token resolution | **Internal query plan** | **Unchanged** |
| Auction-house filter | **Unchanged** JPQL on `auctionHouse` | **Unchanged** |
| Export XLSX | **Unchanged** (`PurchaseExportService` not in V94 diff) | **Unchanged** |
| Portal DTOs | **Unchanged** in commit diffs | **Unchanged** |

**Breaking API changes identified in Git:** **None** at HTTP/controller level.

**Non-breaking response addition:** `chassisKey` (V93+).

---

## 8. Compatibility matrix

| Frontend | Backend | Status | Evidence |
|----------|---------|--------|----------|
| Existing production frontend | Pre-V93 (`8b8f07b`) | **COMPATIBLE** | Current prod pairing (**NOT VERIFIED** deployed SHAs) |
| Existing production frontend | V93 (`ac72656`) | **COMPATIBLE WITH RISKS** | No controller diff; +`chassisKey`; chassis query refactor + tests |
| Existing production frontend | V94 (`2f03ad4`) | **COMPATIBLE WITH RISKS** | Above + norm internal only; Rixo legacy lookup |
| New frontend (from repo HEAD) | V93 | **COMPATIBLE WITH RISKS** | Same API; may expose `chassisKey` in UI if wired |
| New frontend (from repo HEAD) | V94 | **COMPATIBLE WITH RISKS** | Same |

---

## 9. Railway startup and health-check risks

**Verified (repo):**

| Item | Detail |
|------|--------|
| Active prod profile | `SPRING_PROFILES_ACTIVE=docker,prod` in `backend/Dockerfile` |
| Flyway | Enabled; runs on startup |
| Hibernate | `ddl-auto: validate` in `application-prod.yml` |
| V93 then V94 | Single deploy at `2f03ad4` runs both pending migrations sequentially; **two-step** deploy runs V93 only then V94 only |
| Auto repair | **Not** on `docker,prod` (`FlywayDockerRepairConfig` docker-only) |
| Health | `GET /api/actuator/health`; Docker `start-period=90s` |
| Memory | `-Xmx300m` |

**NOT VERIFIED:**

- Railway health timeout vs migration duration on production `purchases` size
- Zero-downtime / blue-green vs replace-in-place
- Whether new instance starts before old stops

**Assessment:** Deployment **can interrupt** backend availability during migration/restart. Existing frontend will show errors until health passes — plan maintenance window / client notice (client informed — operational fit).

---

## 10. V93 migration risk

**File:** `V93__purchases_chassis_key.sql`

| Step | Risk |
|------|------|
| ADD `chassis_key` nullable | Partial state if script fails mid-way |
| UPDATE backfill | Full table scan; lock/time **NOT VERIFIED** without row count |
| MODIFY NOT NULL | Fails if NULLs remain |
| CREATE INDEX | Build time scales with table size |

**Failure:** MySQL DDL commits incrementally; Flyway records **failed** V93 → **startup blocked** until repair/restore. No auto-repair on prod.

**Success:** Column + index; app requires `chassis_key` (validate).

**Duration:** **NOT VERIFIED** without production statistics.

---

## 11. V94 migration risk

**File:** `V94__purchases_auction_house_norm.sql`

| Step | Risk |
|------|------|
| ADD column | Idempotent guard |
| SIGNAL if norm length > 100 | Migration **aborts** if bad data — **safe failure** |
| UPDATE backfill | Full table scan |
| CREATE INDEX | Size-dependent |

**Failure:** Partial column/backfill/index possible; Flyway **failed** row blocks startup.

**Success:** Nullable norm + index; app maps `auctionHouseNorm`.

---

## 12. Production database facts still required

| Fact | Why it matters | How to check | Local? |
|------|----------------|--------------|--------|
| MySQL version | Migration compatibility | `SELECT VERSION()` | **No** — prod read-only |
| SQL mode | Truncation vs error on edge lengths | `@@GLOBAL.sql_mode` | **No** |
| Flyway latest version | Expect ≤ V92 before deploy | `flyway_schema_history` | **No** |
| `purchases` row count / size | Migration window, health timeout | COUNT + `information_schema` | **No** |
| Column definitions | V93 length, V94 overflow precheck | `information_schema.columns` | **No** |
| Oversized norm/chassis counts | V94 SIGNAL / V93 backfill | Pre-check SQL in staging docs | **No** |
| Storage headroom | Index + column | Railway dashboard | **No** |
| Backup + restore test | Recovery | `backup-railway-mysql.sh` + test restore | **No** (prod policy) |
| Railway deploy strategy | Downtime | Dashboard | **NOT VERIFIED** |
| Health-check timeout | Failed deploy during long V93/V94 | Dashboard | **NOT VERIFIED** |
| Vercel deployed commit SHA | Old frontend compatibility proof | Vercel deployments | **NOT VERIFIED** |

**Do not invent values.**

---

## 13. Backup and rollback analysis

| Topic | Finding |
|-------|---------|
| Full backup before deploy | **Required** (native + logical `.sql.gz` per `docs/railway-mysql-backup.md`) |
| Backup script | **Usable with operator discipline**; no prod/staging guard in script |
| Restore tested | **NOT VERIFIED** |
| Roll back app to V93 after V94 DB | **Invalid** — Flyway: V94 in history, V93 JAR missing V94 script → **startup blocked** |
| Roll back app to pre-V93 after V93+ DB | **Blocked** if DB has `chassis_key` and app expects validate — need DB restore |
| V94 succeeds, app deploy fails | Redeploy V94 artifact; DB OK |
| Migration fails | Startup blocked; **repair** after root cause or **restore** backup; do not drop columns by default |

**Recovery preference:** Forward-fix or **restore verified backup** > dropping columns.

---

## 14. Recommended production deployment sequence

Aligned with your staged plan; **do not execute** here.

### Stage 1 — Preflight (**Go:** all checks pass)

- Read-only prod metadata (where permitted): Flyway version, MySQL version/SQL mode, row counts, pre-check SQL for length risks.
- Verified backup + optional restore drill on clone.
- Confirm Vercel live commit (**NOT VERIFIED** until done).
- Review Railway health timeout / maintenance window.
- Confirm no failed Flyway rows.

**Stop if:** Flyway ≠ expected baseline, overflow counts > 0, backup missing, or unresolved failed migration.

### Stage 2 — V93 backend (**Go:** health + smoke)

- Deploy **`ac726562`** image to production Railway backend only (frontend unchanged).
- Flyway V93 runs on startup; monitor logs and duration.
- Verify `GET /api/actuator/health`.
- Verify existing frontend: login, purchase list/page, create/update, chassis search, booking search, export sample.
- Watch for 502 during migration.

**Stop if:** Flyway fails, health never green, critical workflow regression.

### Stage 3 — V94 backend (**Go:** after V93 stable + new backup)

- **Take post-V93 backup.**
- Deploy **`2f03ad4`** image.
- Flyway V94; monitor SIGNAL risk (overflow precheck should be zero).
- Health + frontend smoke + auction filter + Rixo mapping price update spot check.

**Stop if:** Flyway fails — **do not** roll back to V93 binary on V94 DB.

### Stage 4 — New Vercel frontend (**Go:** after backend stable)

- Deploy new frontend; confirm `/api` still proxies to same Railway backend (`vercel.json` unchanged unless intentional).
- End-to-end regression.

---

## 15. Stage go/no-go criteria (condensed)

| Stage | Go | No-go |
|-------|-----|--------|
| Preflight | Backup OK; Flyway at V92; prechecks 0; window agreed | Unknown schema; no backup |
| V93 | V93 success; health OK; frontend smoke OK | Failed migration; prolonged outage |
| V94 | V94 success; health OK; frontend smoke OK | Failed migration; attempt V93-only app on V94 DB |
| Frontend | Backend stable ≥ agreed period | Backend unstable |

---

## 16. Unresolved risks and blockers

1. Production DB size / migration time vs 90s health — **NOT VERIFIED**
2. Deployed Vercel commit vs assumed API client — **NOT VERIFIED**
3. `chassisKey` visible in JSON (minor UX/debug; unlikely break in-repo clients)
4. Chassis indexed lookup equivalence under stale keys — **low** with normal JPA writes
5. Client usage during business hours — **operational** (mitigate with window + comms)
6. Railway rolling deploy behavior — **NOT VERIFIED**

---

## 17. Final verdict

**READY AFTER SPECIFIED PRECONDITIONS**

**Not** **READY FOR PRODUCTION DEPLOYMENT** until:

1. Production preflight SQL and Flyway baseline verified  
2. Verified backup (and restore drill if policy requires)  
3. Vercel deployed SHA documented (or acceptance of NOT VERIFIED risk)  
4. Railway health/migration window planned  
5. Staging validation of V93 → V94 recommended (see staging docs) though not strictly in repo gate  

**Technical conclusion on strategy:** Keeping **existing Vercel frontend** while upgrading **Railway backend** V93 then V94 is **compatible with risks** at the **HTTP API layer**; primary risks are **operational** (downtime, migration failure, Flyway rollback constraints), not frontend JSON breakage for the in-repo client patterns.

---

## 18. Actions not performed

- No Railway/Vercel deploy  
- No production MySQL/SQL  
- No Flyway, backup, restore  
- No env or code changes  
- No commit/push  

---

## Appendix — Files / evidence consulted

- Git: `8b8f07b`, `ac726562`, `2f03ad4`; diffs on controllers, `Purchase.kt`, `PurchaseRepository.kt`
- `vercel.json`, `public/automan/vercel.json`
- `src/jsMain/.../ApiService.kt`, `ApiClient.kt`, `Utils.kt`
- `backend/Dockerfile`, `application-prod.yml`, `application.yml`, `FlywayDockerRepairConfig.kt`
- `V93__purchases_chassis_key.sql`, `V94__purchases_auction_house_norm.sql`
- Staging/rollback prior documentation in `docs/staging-v93-v94-*.md`
