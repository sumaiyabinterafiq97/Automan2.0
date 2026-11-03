# GitHub release preparation audit

**Date:** 2026-10-30 (local repository inspection)  
**Scope:** Read-only — no commit, push, deploy, or code changes.  
**Repository:** Automan2.0 — `origin` → `https://github.com/sumaiyabinterafiq97/Automan2.0.git`

---

## 1. Current Git state

| Item | Value |
|------|--------|
| Branch | `master` |
| HEAD | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` |
| `origin/master` (after `git fetch`) | `8b8f07b1252869c7c46245f07f96c97ae50c9b51` |
| Commits ahead of `origin/master` | **2** |
| Index (staged) | **Empty** |
| Working tree | **Dirty** — modified tracked files + many untracked files |

### Modified tracked files (not staged)

| Path | Category |
|------|----------|
| `src/jsMain/kotlin/com/automan/purchase/CarBooking.kt` | New frontend — Create Shipping list filter |
| `src/jsMain/kotlin/com/automan/purchase/CnfFobCalculation.kt` | New frontend — C&F/FOB row drag order |
| `src/jsMain/kotlin/com/automan/purchase/MinimalPurchaseApp.kt` | New frontend — PDF order sync, booking suggestions |
| `src/jsMain/kotlin/com/automan/purchase/Utils.kt` | New frontend — `reorderListByKey` |
| `src/jsTest/kotlin/com/automan/purchase/utils/UtilsBusinessTest.kt` | New frontend — test |
| `src/jsMain/resources/styles.css` | New frontend — booking + C&F styles |
| `src/jsMain/resources/index.html` | Cache-bust query params only (`?v=` on CSS/JS) — **not in user’s six-file list** |
| `docs/railway-mysql-backup.md` | Unrelated doc tweak |
| `.gradle/*`, `backend/.gradle/*` | **Exclude** — build noise |

### Untracked files (partial list — do not bulk-add)

| Path | Category |
|------|----------|
| `docs/production-v93-v94-release-readiness-audit.md` | Production-readiness documentation |
| `docs/staging-v93-v94-readiness-plan.md` | Staging documentation |
| `docs/staging-v93-v94-phase2-architecture.md` | Staging documentation |
| `docs/staging-v93-v94-phase3a-railway-database.md` | Staging documentation |
| `docs/railway-security-incident-response.md` | Ops documentation |
| `Automan Feedbacks + PRD.md`, `files/*.pdf`, `tmp/`, SQL/CSV/XLSX/PDF imports | **Exclude** — unrelated / sensitive / temp |
| `automan_local_pre_v93_backup.sql` | **Exclude** — backup |

---

## 2. Existing local commits (not on GitHub yet)

| Commit | SHA | Summary |
|--------|-----|---------|
| V93 | `ac72656206ad8ae3a62e0116d019edec714d6957` | `perf: optimize purchase chassis lookups` — **54 files**, backend + `V93__purchases_chassis_key.sql` |
| V94 | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` | `feat: add V94 auction house normalization` — **13 files**, backend + `V94__purchases_auction_house_norm.sql` |

**Verified:** V93/V94 commits on `master` contain **no** `src/jsMain/**` changes. All recent booking / C&F UI work is **uncommitted** working-tree edits only.

**Remote tracking:** `master` is **2 commits ahead** of `origin/master`; merge-base with `origin/master` is `8b8f07b`.

---

## 3. New frontend changes (uncommitted)

| Feature | Files | Behavior (from implementation) |
|---------|-------|--------------------------------|
| Create Shipping — list chassis filter | `CarBooking.kt`, parts of `MinimalPurchaseApp.kt`, `styles.css` | Client-side filter on loaded list; in-memory selection; no suggestion dropdown on Create; Recreate unchanged |
| C&F / FOB — row drag order | `CnfFobCalculation.kt`, `Utils.kt`, `styles.css`, `UtilsBusinessTest.kt` | ⋮⋮ grip; DOM reorder of main+detail rows; sync `cnfPageSelectedCars` + `globalSelectedCarsForPdf` |
| Shipping PDF order (C&F page) | `MinimalPurchaseApp.kt`, `CnfFobCalculation.kt` | `syncGlobalSelectedCarsForPdfFromCnfOrder()` before Preview/PDF when not on booking form |
| Shared helper | `Utils.kt` | `reorderListByKey()` |

**Approximate diff size (six Kotlin/CSS/test files):** ~410 insertions, ~93 deletions (per `git diff --stat`).

**Optional for same release commit:** `src/jsMain/resources/index.html` — only bumps `?v=` cache keys for `styles.css` and `automan-car-purchase.js` after local builds; recommend including **if** you commit frontend so browsers pick up new assets after Vercel deploy.

---

## 4. Test results (Phase 2)

### Automated (executed this audit)

| Check | Result |
|-------|--------|
| `./gradlew compileKotlinJs` | **PASS** (exit 0) |
| `./gradlew jsTest` | **PASS** (exit 0) |

Includes `reorderListByKeyMovesOneItemToTargetIndex` and existing backend/JS tests in the current **dirty** tree (with uncommitted frontend changes compiled).

### Manual / browser (NOT executed in this audit)

The following were **not** verified in a browser during this task; treat as **pre-release manual checklist**:

- Create Shipping: filter, selection while filtered, select-all on visible rows, clear search, stock/country reload, back from C&F selection restore  
- C&F/FOB: drag reorder, expanded row stays paired, PDF Preview/download row order, Save shipping-history item order  
- Mobile card labels for C&F table  

**Report:** No automated test failures. Manual regression **NOT VERIFIED** here.

---

## 5. Exact files recommended for frontend commit

Stage **only** these paths (no `git add .`):

```
src/jsMain/kotlin/com/automan/purchase/CarBooking.kt
src/jsMain/kotlin/com/automan/purchase/CnfFobCalculation.kt
src/jsMain/kotlin/com/automan/purchase/MinimalPurchaseApp.kt
src/jsMain/kotlin/com/automan/purchase/Utils.kt
src/jsTest/kotlin/com/automan/purchase/utils/UtilsBusinessTest.kt
src/jsMain/resources/styles.css
```

**Recommended addition (same commit):**

```
src/jsMain/resources/index.html
```

Reason: cache-bust only; helps Vercel/CDN load new CSS/JS after deploy.

**Do not stage** in the frontend commit: `.gradle`, `docs/railway-mysql-backup.md`, staging/production audit docs (unless you intentionally want a separate docs commit), PDFs, `tmp/`, SQL backups, PRDs.

---

## 6. Unrelated files excluded

- All `.gradle` / `backend/.gradle` artifacts  
- `docs/railway-mysql-backup.md` (unless separately reviewed)  
- Untracked staging docs, PRDs, PDFs, CSV/XLSX, `tmp/`, `files/`, `automan_local_pre_v93_backup.sql`  
- V93/V94 are already **committed** — do not re-stage unless amending (not recommended)

---

## 7. Exact impact of `git push origin master` (without new frontend commit)

If you push **today’s** `master` **as-is** (only V93 + V94 commits; frontend still local):

### Reaches GitHub

- Commits: `ac726562` (V93), `2f03ad4` (V94)  
- **Backend-only** diff vs `8b8f07b`: migrations `V93__purchases_chassis_key.sql`, `V94__purchases_auction_house_norm.sql`, services/repos/tests, `Purchase.kt`, normalizers, etc.  
- **Does not** include uncommitted `CarBooking.kt`, `CnfFobCalculation.kt`, etc.

### Does not reach GitHub (until committed)

- Create Shipping list filter  
- C&F/FOB drag order + PDF order sync  
- Local `styles.css` / `index.html` cache bumps  

---

## 8. Railway automatic deployment implications

**Local repo evidence:** Production API host referenced in [`vercel.json`](vercel.json): `automan20-production.up.railway.app`.  
**NOT VERIFIED in this audit:** Railway dashboard “deploy branch”, health timeout, zero-downtime strategy.

**If Railway is wired to deploy `master` on push (typical):**

| Event | Effect |
|-------|--------|
| Push V93+V94 | New backend image build + deploy |
| Application startup | Spring `docker,prod` profile — **Flyway runs on startup** (per production audit) |
| Pending migrations | **V93** then **V94** execute sequentially if DB is below those versions |
| Risk | Migration duration / locking; failed migration **blocks startup**; no auto-repair on prod profile |

**Uncommitted frontend:** Irrelevant to Railway until pushed; backend deploy does not include new JS.

---

## 9. Vercel automatic deployment implications

**Local repo evidence:**

- Root [`vercel.json`](vercel.json): `buildCommand`: `./gradlew jsBrowserProductionWebpack prepareVercelPublic`, `outputDirectory`: `public`  
- `/api/*` → production Railway backend  

**NOT VERIFIED:** Vercel project linked branch, last production deployment SHA, preview vs production.

**If Vercel auto-deploys `master` on push:**

| Event | Effect |
|-------|--------|
| Push V93+V94 only | Frontend bundle built from **`2f03ad4`** — **same Kotlin/JS sources as `8b8f07b`** for booking/C&F (no new UI commits) |
| Production Vercel JS | **Unchanged feature-wise** vs pre-push `master` tip on GitHub until a **frontend commit** is pushed |
| After frontend commit + push | New booking filter + C&F drag + styles deploy; still proxies `/api` to same Railway host |

**Compatibility (from [`docs/production-v93-v94-release-readiness-audit.md`](production-v93-v94-release-readiness-audit.md)):** Existing production frontend **COMPATIBLE WITH RISKS** with V93/V94 backend at HTTP layer (`chassisKey` in JSON; no controller breaks). Deployed Vercel SHA **NOT VERIFIED**.

---

## 10. Production deployment blockers

### Gate: production preflight (Phase 5)

Per [`docs/production-v93-v94-release-readiness-audit.md`](production-v93-v94-release-readiness-audit.md):

- Production DB preflight **not confirmed** (Flyway baseline, row counts, SQL mode, backup restore drill, etc.)  
- Verdict there: **READY AFTER SPECIFIED PRECONDITIONS** / **BLOCKED** until preflight  

**If `git push origin master` triggers Railway production deploy with Flyway V93/V94:**

## **BLOCKED — PRODUCTION PREFLIGHT NOT VERIFIED**

Do not treat push as safe for production backend until:

1. Verified backup (+ restore drill if required)  
2. Read-only production metadata (Flyway version, MySQL version/mode, migration prechecks)  
3. Agreed maintenance window and rollback plan  

Pushing **frontend-only** commits (after separate commit) to GitHub/Vercel does **not** run Flyway but still changes user-facing behavior; coordinate with backend rollout strategy.

---

## 11. Recommended next steps

1. **Do not push `master` to production Railway** until production preflight is complete — gate above.  
2. **Manual browser regression** on the six frontend files (checklist in §4).  
3. **Create a dedicated frontend commit** staging only the paths in §5 (and optionally `index.html`).  
4. **Decide commit strategy:**  
   - **Option A:** Push V93+V94 only after preflight (backend release).  
   - **Option B:** Frontend commit first on GitHub without Railway caring (if Railway only builds `backend/` — **NOT VERIFIED**; assume full-repo deploy).  
   - **Option C:** Staging Railway + staging DB validate V93→V94, then production (see staging docs).  
5. **Separate commits** for staging/production **documentation** if desired — do not mix with UI commit.  
6. After frontend push: run `./gradlew jsBrowserProductionWebpack prepareVercelPublic` in CI/Vercel (already in `vercel.json`).  
7. Re-verify `origin/master` SHA on GitHub after any push.

---

## 12. Git verification (before / after this audit)

| Check | Before audit | After audit |
|-------|--------------|-------------|
| Branch | `master` | `master` (unchanged) |
| HEAD | `2f03ad4…` | `2f03ad4…` (unchanged) |
| Index | Empty | Empty |
| Application code | Modified (uncommitted) | **Unchanged** — only this doc added |
| New file | — | `docs/github-release-preparation-audit.md` |

**Confirmation:** No application source was modified for this audit. No stage, commit, push, deploy, or production access.

---

## Appendix — Change classification summary

| Bucket | On GitHub today (`origin/master`) | Local commits not pushed | Uncommitted working tree |
|--------|----------------------------------|---------------------------|---------------------------|
| V93 backend | No | Yes (`ac72656`) | — |
| V94 backend | No | Yes (`2f03ad4`) | — |
| New frontend UI | No | No | Yes (6 Kotlin/CSS/test files + optional `index.html`) |
| Staging docs | No | No | Untracked |
| Production audit doc | No | No | Untracked |
| Unrelated / noise | — | — | `.gradle`, PDFs, tmp, backup SQL, PRD, etc. |
