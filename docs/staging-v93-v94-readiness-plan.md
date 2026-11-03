# Staging readiness plan — V93 / V94 (Automan2.0)

**Document purpose:** Evidence-based plan to validate commits `ac72656` (V93) and `2f03ad4` (V94) in an **isolated staging** environment before production deployment.

**Scope of this document:** Phase 1 (read-only repository audit) is **complete**. Phases 2–5 are **proposed** and **not executed**.

**Last audited:** 2026-10-30  
**Repository HEAD:** `2f03ad4c718f015633dc58545d09b8d7bb2e0777` on `master`

---

## 1. Executive summary and readiness verdict

| Area | Status | Notes |
|------|--------|--------|
| Git / release commits | **READY** | Both commits present locally; index empty |
| Staging Railway backend + MySQL | **BLOCKED** | No staging project/services defined in repo |
| Staging Vercel frontend isolation | **BLOCKED** | Repo rewrites hardcode production API host |
| Backup/restore tooling | **READY** (with safeguards) | Scripts target whatever `.env.railway.backup` points at |
| V93/V94 test sequence | **REQUIRES APPROVAL** | Needs staging infra + optional prod data copy approval |
| Production safety (this task) | **READY** | No production access performed |

**Overall readiness verdict:** **BLOCKED — staging infrastructure required** before any V93/V94 staging deployment.

**Distinction:**

- **Prepare plan (this document):** DONE.
- **Create staging environment:** NOT DONE — requires Railway/Vercel setup and human approval.
- **Execute staging deployments:** NOT AUTHORIZED in this task.

---

## 2. Current infrastructure findings (Phase 1)

### 2.1 Railway / backend

| Finding | Evidence |
|---------|----------|
| No `railway.toml` / `railway.json` in repository | Glob search — **NOT VERIFIED** in Railway dashboard |
| Backend image built from `backend/Dockerfile` | Multi-stage build; JAR `automan-backend-1.0-SNAPSHOT.jar` |
| Default image env | `ENV SPRING_PROFILES_ACTIVE=docker,prod` |
| Health check | `GET /api/actuator/health`; `start-period=90s` (may be tight for large migrations) |
| Memory | `-Xmx300m`; comment references ~500MB Railway limit |
| Separate staging DB | **Supported without code change** via `SPRING_DATASOURCE_*` in `application-docker.yml` |
| Accidental prod DB from code | **No hardcoded prod JDBC URL in prod profile** — connection is env-driven |
| Hibernate validation | **`docker,prod` required** for safe staging parity: `application-prod.yml` sets `ddl-auto: validate` |
| Flyway at startup | `spring.flyway.enabled: true` in `application.yml`; migrations in `classpath:db/migration` |
| Auto repair on prod | **`FlywayDockerRepairConfig`** only `@Profile("docker", "docker-local")` — **NOT** active for `docker,prod` |
| Flyway baseline | Prod profile: `baseline-on-migrate: true`, `baseline-version: 1` — history table must exist and be correct |

**Stack versions (repo):** Spring Boot **3.2.0**, Flyway core + mysql (Boot-managed), MySQL connector **8.0.33**, Kotlin **1.9.21**.

**Flyway validation (from dependency behavior, not executed):** Default validate-on-migrate applies. An app artifact **without** migration `V94` **cannot** start against a database whose history shows **successful V94**. A **failed** migration row blocks startup until manual repair/restore.

### 2.2 Frontend hosting

| Finding | Evidence |
|---------|----------|
| Root `vercel.json` | Rewrites `/api/*` → `https://automan20-production.up.railway.app/api/*` |
| `public/automan/vercel.json` | Same production destination |
| Kotlin JS API base | `API_BASE_URL = "/api"` in `Utils.kt` — relative; depends on host rewrite |
| Legacy JS bundles | `public/automan/*.js`, `src/jsMain/resources/*.js` use `/api` or `window.API_BASE_URL = '/api'` |
| Hardcoded production Railway URL in TS/Kotlin | **Not found** — production coupling is in **Vercel rewrite JSON** |
| Vercel preview safety | **NOT VERIFIED** — previews typically use repo `vercel.json` unless overridden in Vercel project settings |

**Risk:** Any Vercel deployment using committed rewrites without override will proxy browser traffic to **production API**.

### 2.3 Backup and restore scripts

| Finding | Evidence |
|---------|----------|
| Backup | `scripts/backup-railway-mysql.sh` — `mysqldump` via `.env.railway.backup` |
| Restore | `scripts/restore-railway-mysql.sh` — requires `CONFIRM=YES`; prints host/database name before import |
| Target selection | **Single env file** — `ENV_FILE` override possible but no built-in prod/staging guard |
| Accidental prod restore | **Possible** if `.env.railway.backup` contains production TCP credentials |
| Backup verification | `gzip -t`, minimum size check in backup script |
| Pre-restore DB verification | **No** automatic staging-vs-prod check — operator must verify host/database name |
| Docs | `docs/railway-mysql-backup.md`, `.env.railway.backup.example` |

**Recommendation:** Use a **separate** gitignored env file (e.g. `.env.railway.staging.backup`) and naming conventions for staging hosts — **REQUIRES APPROVAL** to create/populate.

### 2.4 Git safety (read-only)

| Check | Result |
|-------|--------|
| Branch | `master` |
| HEAD | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` |
| V93 commit | `ac72656206ad8ae3a62e0116d019edec714d6957` — present |
| V94 commit | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` — present |
| Staged changes | **0** |
| Unrelated work | Modified `.gradle/**`, `docs/railway-mysql-backup.md`; many untracked PDFs/tmp/files |

---

## 3. Existing staging capabilities and missing infrastructure

### Present in repository

- Docker backend build with `docker,prod` defaults
- Flyway SQL/Kotlin migrations through **V94**
- Local/ops backup and restore scripts
- Portal release checklist (`docs/portal-release-checklist.md`) — partially stale (references V85–V91; repo has V92–V94)
- Security/backup runbooks (`docs/railway-security-incident-response.md`)

### Missing (not in repo — **BLOCKED** until created)

- Dedicated Railway **staging** project or environment
- Staging MySQL service (separate volume)
- Staging backend service URL
- Staging Vercel project or environment-specific rewrites
- Documented staging env var set (names only in this plan)
- CI/build pipeline pinning image to commit SHAs for two-step deploy
- Confirmation of MySQL version / SQL mode on staging

---

## 4. Proposed staging architecture (Phase 2 — not executed)

### A. Railway staging backend

- New Railway service (e.g. `automan-backend-staging`) in a **non-production** project/environment.
- Deploy Docker image built from git SHA (Commit 1, then Commit 2).
- **Do not** reuse production MySQL references.

### B. Railway staging MySQL

- New MySQL plugin/service; unique volume.
- Record `VERSION()` and `@@sql_mode` after creation.
- Populate via:
  - **Option 1 (preferred for parity):** Restore sanitized clone — **REQUIRES APPROVAL** for prod data.
  - **Option 2:** Empty DB + Flyway through V92 — may not reflect prod edge cases.
- Independent backups before V93 and before V94 deploys.

### C. Vercel staging frontend

- Separate Vercel project **or** environment-specific rewrite override.
- API rewrite must target **staging Railway host only** (placeholder: `https://<staging-backend>.up.railway.app/api/:match*`).
- Verify in browser Network tab that no request hits `automan20-production`.

### D. External integrations (staging-safe)

| Integration | Staging recommendation |
|-------------|------------------------|
| Email (Resend) | Omit `RESEND_API_KEY` or use test key + test inbox |
| Gmail (Rixo) | Omit `GMAIL_*` or use dedicated test account |
| R2 | `R2_ENABLED=false` or separate bucket + credentials |
| `APP_FRONTEND_URL` | Staging site URL only |
| `APP_VERIFICATION_BASE_URL` | Staging API `/api` base |
| Signup/admin mail | Disable or route to test addresses |

---

## 5. Required environment variables (placeholders only)

Set on **staging backend** (Railway Variables):

| Variable | Example placeholder | Required |
|----------|---------------------|----------|
| `SPRING_PROFILES_ACTIVE` | `docker,prod` | Yes |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<staging-host>:<port>/<db>?useSSL=...` | Yes |
| `SPRING_DATASOURCE_USERNAME` | `<staging-user>` | Yes |
| `SPRING_DATASOURCE_PASSWORD` | `<staging-password>` | Yes |
| `PORT` | `<railway-injected>` | Usually auto |
| `APP_FRONTEND_URL` | `https://<staging-frontend-host>` | Strongly recommended |
| `APP_VERIFICATION_BASE_URL` | `https://<staging-backend-host>/api` | Strongly recommended |
| `RESEND_API_KEY` | *(unset for staging)* | Optional |
| `APP_MAIL_ADMIN_EMAIL` | `staging-admin@example.test` | If mail tested |
| `R2_ENABLED` | `false` | Recommended |
| `R2_*` | *(unset)* | If R2 off |

**Never** copy production secret values into this document or into shared chat.

---

## 6. Frontend routing isolation plan

1. Create **separate** Vercel project for staging **or** configure **Preview** / **Staging** environment rewrite in Vercel dashboard to staging Railway URL.
2. **Do not** deploy current root `vercel.json` to a staff staging URL without changing the `/api` rewrite target.
3. After deploy, verify:
   - Open staging site → DevTools → Network → any `/api/...` request goes to **staging** host.
4. Optional hardening (future code change — **not in this task**): environment-based rewrite or build-time API base — currently **not** in Kotlin/webpack for production path.

**Status:** **REQUIRES APPROVAL** for Vercel/Railway hostnames and rewrite configuration.

---

## 7. Backup/restore safety assessment

| Control | Status |
|---------|--------|
| Scripts can target staging | **READY** if env file points to staging TCP proxy |
| Accidental prod target | **BLOCKED** without operator discipline — add staging-only env file + checklist |
| `CONFIRM=YES` gate on restore | **READY** |
| Backup integrity checks | **READY** (`gzip -t`, min size) |
| Pre-restore hostname verification | **REQUIRES APPROVAL** — manual checklist item before `CONFIRM=YES` |

**Pre-restore operator checklist (mandatory):**

1. Confirm Railway project/environment name is **staging**.
2. Confirm MySQL service is **not** production (service name, dashboard label).
3. Confirm `RAILWAY_MYSQL_HOST` / `RAILWAY_MYSQL_DATABASE` match staging documentation.
4. Take staging backup before destructive restore.

---

## 8. Exact V93 → V94 deployment sequence (Phase 3 — proposed, not executed)

### Prerequisites (**REQUIRES APPROVAL**)

- Staging backend + MySQL + frontend isolation live.
- Staging DB at expected Flyway version (typically **V92** if cloned from current prod — **NOT VERIFIED**).
- Pre-migration SQL on **staging only** (Section 9).
- Staging backup: `BACKUP_LABEL=pre-v93-staging`.

### Stage A — Deploy Commit 1 (`ac72656`)

1. Build/push backend image from `ac72656206ad8ae3a62e0116d019edec714d6957`.
2. Deploy to **staging backend only**; single replica preferred during migration.
3. Watch logs for Flyway **V93**; record duration.
4. Run post-migration SQL (Section 10).
5. Confirm `GET /api/actuator/health` → 200.
6. Run smoke tests (Section 11) focused on chassis lookups and purchase CRUD.

**Success criteria:** Flyway V93 `success=1`; `chassis_key` NOT NULL; mismatch count 0; index present; workflows pass.

### Stage B — Backup + Deploy Commit 2 (`2f03ad4`)

1. Staging backup: `BACKUP_LABEL=post-v93-pre-v94-staging`.
2. Build/push image from `2f03ad4c718f015633dc58545d09b8d7bb2e0777`.
3. Deploy to staging; watch Flyway **V94**; record duration.
4. Post-migration SQL for V94.
5. Health + full smoke tests including Rixo mapping price sync (legacy auction-house trim lookup unchanged).

**Success criteria:** Flyway V94 success; norm backfill mismatch 0; index present; Rixo sync still updates matching purchases.

---

## 9. SQL preflight checks (staging only — do not run on production)

Run on **staging** after DB is created/restored, **before** first migration deploy:

```sql
SELECT VERSION();

SELECT @@GLOBAL.sql_mode AS global_sql_mode,
       @@SESSION.sql_mode AS session_sql_mode;

SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 15;

SELECT column_name, column_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'purchases'
  AND column_name IN ('chassis', 'chassis_key', 'auction_house', 'auction_house_norm');

SELECT COUNT(*) AS purchase_count FROM purchases;

SELECT
    ROUND(data_length / 1024 / 1024, 2) AS data_mb,
    ROUND(index_length / 1024 / 1024, 2) AS index_mb
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'purchases';

SELECT COUNT(*) AS oversized_chassis_key_count
FROM purchases
WHERE CHAR_LENGTH(LOWER(TRIM(chassis))) > 100;

SELECT COUNT(*) AS oversized_auction_house_norm_count
FROM purchases
WHERE CHAR_LENGTH(UPPER(TRIM(COALESCE(auction_house, '')))) > 100;
```

**Expected before V93 (typical clone at V92):**

- Latest successful Flyway version **92** (verify description matches `V92__*`).
- `chassis_key` / `auction_house_norm` columns **absent**.
- `chassis.character_maximum_length` **100** (per `V1__init_multiplatform.sql`).
- Both oversized counts **0**.

---

## 10. Post-migration verification SQL (staging)

### After V93

```sql
SELECT version, description, success FROM flyway_schema_history WHERE version = '93';

SELECT COUNT(*) AS chassis_key_null_count
FROM purchases WHERE chassis_key IS NULL;

SELECT COUNT(*) AS chassis_key_mismatch_count
FROM purchases
WHERE chassis_key <> LOWER(TRIM(chassis));

SELECT index_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'purchases'
  AND index_name = 'idx_purchases_chassis_key';
```

### After V94

```sql
SELECT version, description, success FROM flyway_schema_history WHERE version = '94';

SELECT COUNT(*) AS auction_house_norm_null_count
FROM purchases WHERE auction_house_norm IS NULL;

SELECT COUNT(*) AS auction_house_norm_mismatch_count
FROM purchases
WHERE auction_house_norm <> UPPER(TRIM(COALESCE(auction_house, '')));

SELECT index_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'purchases'
  AND index_name = 'idx_purchases_auction_house_norm';
```

---

## 11. Staging smoke-test checklist (Phase 5 — proposed)

Use **staging URLs only**. Do not exercise flows that send real customer email.

| # | Area | Endpoint / workflow | Expected | Failure |
|---|------|---------------------|----------|---------|
| 1 | Health | `GET /api/actuator/health` | 200 UP | Non-200, timeout |
| 2 | Login | `POST /api/auth/login` | 200 + session | 401/5xx |
| 3 | Permissions | Staff pages after login | Authorized routes load | 403 loops |
| 4 | Purchase list | `GET /api/purchases/page` | Paginated data | 5xx, empty errors |
| 5 | Create | `POST /api/purchases` | 200; DB norms/keys set | 409/500 |
| 6 | Update | `PUT /api/purchases/{id}` | Persists | 404/500 |
| 7 | Chassis lookup | `GET /api/purchases/search`, `/search-chassis`, `/cars/search` | Correct matches | Wrong/empty |
| 8 | Auction filter | `GET /api/purchases/filter/auction-house` | Same behavior as pre-change | Unexpected regression |
| 9 | Pagination/sort | `/page`, `/sort` | Stable ordering | Errors |
| 10 | Export | `GET /api/purchases/export/xlsx` | Valid file | 5xx |
| 11 | Rixo sync | `PUT /api/rixo-mapping/{id}` price change | Matching purchases update via **legacy** trim lookup | No update / error |
| 12 | Client portal | `GET /api/portal/client/me`, `/cars/list` | 200 scoped | 403/5xx |
| 13 | Consignee portal | Consignee routes | Read-only OK | 403/5xx |
| 14 | Frontend routing | Network tab | All `/api` → **staging** host | Any production host |
| 15 | Mobile/narrow UI | Purchase list cards | Usable layout | Broken UI |
| 16 | Email isolation | Signup/verify/mail flows | No real customer delivery | External mail received |
| 17 | R2 isolation | Media upload | Disabled or test bucket only | Prod bucket writes |

---

## 12. Rollback compatibility matrix (Phase 4)

Based on repository Flyway classpath + Hibernate `validate` (Spring Boot 3.2 / Flyway 9.x defaults).

| DB state | App artifact | Startup | Recovery |
|----------|--------------|---------|----------|
| Pre-V93 (e.g. V92) | Pre–Commit 1 (`8b8f07b`) | **Safe** | N/A |
| Pre-V93 | Commit 1 (`ac72656`) | **Blocked** (missing `chassis_key`) | Apply V93 |
| Pre-V93 | Commit 2 (`2f03ad4`) | **Blocked** (missing columns) | Apply V93+V94 |
| V93 success | Pre–Commit 1 | **Safe** (extra column OK) | N/A |
| V93 success | Commit 1 | **Safe** | N/A |
| V93 success | Commit 2 | **Blocked** (missing `auction_house_norm`) | Apply V94 |
| V94 success | Commit 1 | **Blocked** (Flyway: V94 applied, script missing in JAR) | **Restore DB to V93** or keep Commit 2 app |
| V94 success | Commit 2 | **Safe** | N/A |
| Failed V93 or V94 | Any | **Blocked** (Flyway failed row) | **Repair** after fix, or **restore** backup |
| V94 partial DDL | Any | **Blocked** until history resolved | Repair + redeploy Commit 2, or restore |

**Key rules (verified from repo behavior):**

1. **V94-successful DB + V93-only artifact is NOT a valid rollback.**
2. Flyway and Hibernate are **separate** checks; both run at startup.
3. MySQL DDL can leave partial schema; Flyway history may show `success = 0`.
4. **`flyway repair` is not automatic in `docker,prod`** — use deliberately after root-cause fix.
5. Do not drop V93/V94 columns as casual rollback; prefer forward-fix or restore.

---

## 13. Failure recovery procedures (staging)

| Scenario | Procedure |
|----------|-----------|
| V93 fails | Stop deploys; inspect `flyway_schema_history` and partial `chassis_key`; **restore** pre-V93 staging backup **or** `flyway repair` + redeploy Commit 1 after fixing cause |
| V94 fails before/during backfill/index | Stop; inspect partial `auction_house_norm`; **restore** post-V93 backup **or** repair + redeploy Commit 2 (V94 SQL is idempotent for column/index; backfill `WHERE … IS NULL`) |
| App deploy fails after migrations | Redeploy same or newer artifact matching DB version — **do not** deploy Commit 1 onto V94 DB |
| Repair | Run only after understanding failure; **never** repair blindly; prod/staging: manual CLI or controlled process — **not** enabled in `docker,prod` |

---

## 14. Required human approvals and external setup

| Step | Status |
|------|--------|
| Create Railway staging project + MySQL | **REQUIRES APPROVAL** |
| Create Railway staging backend service | **REQUIRES APPROVAL** |
| Configure staging Railway Variables | **REQUIRES APPROVAL** |
| Copy production data to staging | **REQUIRES APPROVAL** (legal/ops) |
| Create/configure Vercel staging + API rewrite | **REQUIRES APPROVAL** |
| Run backup/restore against staging | **REQUIRES APPROVAL** |
| Deploy Commit 1 / Commit 2 to staging | **REQUIRES APPROVAL** |
| Push commits to GitHub / CI | **REQUIRES APPROVAL** (out of scope so far) |

---

## 15. Actions NOT performed (explicit)

- No connection to production or staging databases
- No Flyway migrations executed
- No backup or restore scripts executed
- No Railway or Vercel deploys or configuration changes
- No production env var changes
- No git push, commit, reset, stash, checkout, or clean
- No Docker restarts
- No application code or `vercel.json` modifications (except adding **this** document)

---

## Appendix A — Files inspected (Phase 1)

- `backend/Dockerfile`
- `backend/build.gradle.kts`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-docker.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/kotlin/com/automan/backend/config/FlywayDockerRepairConfig.kt`
- `vercel.json`
- `public/automan/vercel.json`
- `src/jsMain/kotlin/com/automan/purchase/Utils.kt` (API base)
- `src/jsMain/kotlin/com/automan/purchase/ApiClient.kt`
- `src/jsMain/kotlin/com/automan/purchase/ApiService.kt`
- `scripts/backup-railway-mysql.sh`
- `scripts/restore-railway-mysql.sh`
- `.env.railway.backup.example`
- `docs/railway-mysql-backup.md`
- `backend/src/main/resources/db/migration/V1__init_multiplatform.sql` (chassis width)
- `backend/src/main/resources/db/migration/V93__purchases_chassis_key.sql`
- `backend/src/main/resources/db/migration/V94__purchases_auction_house_norm.sql`
- Git state (branch, HEAD, commits, status)

---

## Appendix B — Related local commits

| Commit | SHA | Migration |
|--------|-----|-------------|
| Commit 1 | `ac72656206ad8ae3a62e0116d019edec714d6957` | V93 |
| Commit 2 | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` | V94 |
