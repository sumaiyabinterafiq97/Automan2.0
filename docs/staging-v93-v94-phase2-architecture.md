# Phase 2 — Staging architecture (Automan2.0 V93 / V94)

**Purpose:** Implementation-ready plan for an isolated staging environment to validate:

| Commit | SHA | Migration |
|--------|-----|-----------|
| V93 | `ac72656206ad8ae3a62e0116d019edec714d6957` | `V93__purchases_chassis_key.sql` |
| V94 | `2f03ad4c718f015633dc58545d09b8d7bb2e0777` | `V94__purchases_auction_house_norm.sql` |

**Builds on:** [`docs/staging-v93-v94-readiness-plan.md`](staging-v93-v94-readiness-plan.md) (Phase 1).

**Phase 2 scope:** Architecture and procedures only. **No infrastructure created.** **No production access.**

**Repository HEAD (verified):** `2f03ad4c718f015633dc58545d09b8d7bb2e0777` on `master`

---

## 1. Executive summary

| Item | Status |
|------|--------|
| Phase 1 verdict carried forward | **BLOCKED — staging infrastructure required** |
| Phase 2 architecture document | **READY** (this file) |
| Implementation (Railway/Vercel/DB) | **REQUIRES APPROVAL** — not started |
| Application code changes for staging | **NOT REQUIRED** for basic staging (env + hosting only) |
| Script hardening before staging restore | **REQUIRES APPROVAL** (future script changes) |

**Phase 2 verdict:** **READY TO IMPLEMENT STAGING** once human approvals and external setup are complete. Staging validation itself remains **BLOCKED** until infra exists.

**Facts vs recommendations:** Sections label **Verified (repo)**, **Recommendation**, **NOT VERIFIED (dashboard)**, and **Assumption**.

---

## 2. Phase 1 findings incorporated (summary)

Do not re-audit; use Phase 1 for SQL pre/post checks and smoke lists. Critical points for Phase 2:

- **Verified (repo):** `docker,prod` + Flyway + Hibernate `validate`; datasource via env; no Flyway auto-repair on prod profile.
- **Verified (repo):** Root and `public/automan/vercel.json` rewrite `/api` to production Railway host.
- **Verified (repo):** Backup/restore target = contents of `.env.railway.backup`; no staging/prod guard in scripts.
- **Verified (Flyway behavior):** Commit 1 JAR **cannot** start on V94-successful DB; failed migration blocks all startups until repair/restore.
- **NOT VERIFIED:** Production/staging Railway project names, replica counts, health timeouts in Railway dashboard.

---

## 3. Proposed Railway architecture

### 3.A Railway project structure (Recommendation — do not create yet)

**Assumption:** One dedicated Railway **project** (or strictly isolated **environment**) named distinctly from production, e.g. `Automan-Staging` (exact name **NOT VERIFIED**).

| Proposed service | Role | Notes |
|------------------|------|--------|
| `automan-staging-mysql` | MySQL 8 plugin / volume | **No** shared volume with production |
| `automan-staging-backend` | Spring Boot container | Built from `backend/Dockerfile` at pinned git SHA |

**Networking (NOT VERIFIED until setup):**

- Backend uses Railway-injected JDBC URL or `${{MySQL.*}}` variable references pointing **only** to `automan-staging-mysql`.
- **Never** reference production MySQL service variables in staging backend.

**Status:** **REQUIRES APPROVAL** to create project and services.

---

### 3.B Backend environment-variable specification

**Verified (repo):** Connection and profiles come from `application-docker.yml` + `application-prod.yml` overrides. Defaults in `application.yml` are localhost dev values — **must be overridden** on Railway.

| Variable | Purpose | Mandatory | Safe default on staging? | Must differ from prod? | Value source |
|----------|---------|-----------|---------------------------|-------------------------|--------------|
| `SPRING_PROFILES_ACTIVE` | Activates `docker` + `prod` (validate + Flyway prod rules) | **Yes** | Use `docker,prod` | Same string OK; **different services** | Railway Variable |
| `SPRING_DATASOURCE_URL` | JDBC URL to MySQL | **Yes** | No — must be staging DB | **Yes** (host/db) | Staging MySQL service / manual TCP URL |
| `SPRING_DATASOURCE_USERNAME` | DB user | **Yes** | No | **Yes** | Staging MySQL |
| `SPRING_DATASOURCE_PASSWORD` | DB password | **Yes** | No | **Yes** | Staging MySQL (secret) |
| `PORT` | HTTP port | **Yes** on Railway | Railway injects | N/A | Railway |
| `APP_FRONTEND_URL` | Links in emails/UI that point to frontend | Strongly recommended | localhost default **unsafe** on staging | **Yes** | Staging Vercel URL |
| `APP_VERIFICATION_BASE_URL` | Signup/verify links base API | Strongly recommended | Must be staging API `/api` | **Yes** | `https://<staging-backend-host>/api` |
| `APP_MAIL_ADMIN_EMAIL` | Admin notification recipient | If signup mail tested | Dev-team default may be real | **Yes** (test inbox) | Ops |
| `RESEND_API_KEY` | Resend outbound mail | No | Empty = no send (**Verified** log in `EmailService`) | **Yes** (omit or test key) | Optional |
| `APP_MAIL_VALIDATE_MX` | MX validation on signup | Optional | Can set `false` on staging | Optional | Railway |
| `GMAIL_USERNAME` / `GMAIL_APP_PASSWORD` / `GMAIL_SMTP_*` | Rixo transport email | No | Empty disables Gmail path | **Yes** (omit or test) | Optional |
| `R2_ENABLED` | Car picture storage | No | **`false` recommended** | **Yes** | Railway |
| `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_NAME`, `R2_ENDPOINT` | R2 client | Only if `R2_ENABLED=true` | Omit when disabled | **Yes** if enabled | Separate bucket |
| `MEDIA_*` | Upload limits / TTL | Optional | Defaults in yml | Optional | Railway |

**Placeholders (examples only — not real values):**

```text
SPRING_PROFILES_ACTIVE=docker,prod
SPRING_DATASOURCE_URL=jdbc:mysql://<STAGING_MYSQL_HOST>:<PORT>/<STAGING_DB_NAME>?useSSL=...&serverTimezone=UTC&...
SPRING_DATASOURCE_USERNAME=<staging_user>
SPRING_DATASOURCE_PASSWORD=<staging_password>
APP_FRONTEND_URL=https://<staging-frontend-host>
APP_VERIFICATION_BASE_URL=https://<staging-backend-host>/api
R2_ENABLED=false
```

**Verified (repo):** No production JDBC URL is embedded in `application-prod.yml`. Misconfiguration risk is **wrong Railway Variables**, not hardcoded code.

---

### 3.C Database isolation strategy

| Topic | Guidance | Status |
|-------|----------|--------|
| MySQL version | **Recommendation:** Match production major version (**8.0.x**). App uses `MySQL8Dialect` and `mysql:8.0.33` connector (**Verified**). | Confirm with `SELECT VERSION()` on staging after create |
| SQL mode | Record `@@GLOBAL.sql_mode` on staging and compare to production (**NOT VERIFIED** until both exist) | **REQUIRES APPROVAL** to copy prod settings |
| Separate database | Staging MySQL service with **its own volume** | **REQUIRES APPROVAL** |
| Backend wiring | Staging backend `${{MySQL.*}}` or explicit JDBC **only** to staging service | Operator checklist |
| Prevent prod connection | (1) Separate Railway project/env. (2) Separate Variable set. (3) Pre-flight: log **hostname + database name** from JDBC (no password) before first deploy. (4) Optional: staging-only env file for backup scripts | **Recommendation** |
| Identity before migrations | Run Phase 1 preflight SQL on **staging**; confirm `flyway_schema_history` latest version; confirm no `chassis_key` / `auction_house_norm` before V93 | **REQUIRES APPROVAL** to run SQL on staging |

**Staging dataset options:**

| Option | Customer data | Approval |
|--------|---------------|----------|
| Empty + migrate to V92+ synthetic | No | Lower |
| Restore prod dump to staging | Possible PII | **REQUIRES APPROVAL** |
| Sanitized subset | Reduced PII | **REQUIRES APPROVAL** |

---

### 3.D Deployment behavior (Docker + Flyway + health)

**Verified (repo) — `backend/Dockerfile`:**

| Item | Detail |
|------|--------|
| Build context | `backend/` directory; `./gradlew build -x test` |
| Artifact | `automan-backend-1.0-SNAPSHOT.jar` → `app.jar` |
| Start command | `java -Xmx300m ... -jar app.jar` |
| Default profile in image | `SPRING_PROFILES_ACTIVE=docker,prod` |
| Container healthcheck | `curl -f http://localhost:8083/api/actuator/health` |
| Health start period | **90s** (Dockerfile `HEALTHCHECK`) |

**Verified (repo) — runtime:**

- Flyway runs on application startup (`spring.flyway.enabled: true`).
- Pending **V93** then **V94** run sequentially when deploying Commit 2 in one jump; two-step deploy runs **V93 only** then **V94 only** per artifact.
- **`FlywayDockerRepairConfig`** inactive for `docker,prod` — failed migrations **block** restart until manual repair/restore.

**Recommendation — startup timeout risks:**

- Full-table backfill + index creation on `purchases` may exceed **90s**.
- **NOT VERIFIED:** Railway service health check timeout — review in dashboard and increase during migration deploys.
- Deploy with **one replica** during migration (**Recommendation**).

**NOT VERIFIED (Railway dashboard):** Build command (repo root vs `backend/`), root directory, deploy region, memory limit (>512MB **Recommendation** for migration window).

**Status:** Docker image deploy **READY** without code changes; Railway tuning **NOT VERIFIED**.

---

## 4. Vercel staging frontend isolation

### 4.1 Current behavior (**Verified repo**)

| File | Behavior |
|------|----------|
| `vercel.json` | `/api/:match*` → `https://automan20-production.up.railway.app/api/:match*` |
| `public/automan/vercel.json` | Same production API destination |
| Kotlin/JS clients | Relative `/api` — destination determined by **host rewrite** |

Production frontend routing **must remain unchanged** in the production Vercel project.

### 4.2 Options compared

| Approach | Files / setup | Advantages | Risks | Prevent prod API | Verify destination |
|----------|---------------|------------|-------|------------------|-------------------|
| **A. Separate Vercel project (recommended)** | New project; duplicate build settings; **staging-only** rewrite to staging Railway URL; production project untouched | Strong isolation; previews tied to staging project only | Two projects to maintain | Prod `vercel.json` never deployed from staging project | Browser DevTools → `/api` host; curl staging `/api/actuator/health` via rewrite URL |
| **B. Separate Git branch + project** | Branch could hold staging rewrite (future commit) | Clear audit trail | Requires git change + deploy discipline | Keep prod branch without staging URL | Same as A |
| **C. Vercel env-specific rewrite** | Depends on Vercel feature support for env-based rewrites | Single repo possible | **NOT VERIFIED** whether current Vercel plan supports rewrite env substitution without editing committed JSON | Must confirm in Vercel docs/dashboard | Env-specific preview test |
| **D. Dedicated config file (e.g. `vercel.staging.json`)** | Not in repo today; wire via Vercel “Config File” override in **staging project only** | Prod repo unchanged | Easy to misconfigure if same project | Staging project points at staging JSON only | Network tab |

### 4.3 Recommendation

**Use Approach A: separate Vercel staging project.**

1. Create project e.g. `automan-staging` (**REQUIRES APPROVAL**).
2. Same build: `./gradlew jsBrowserProductionWebpack prepareVercelPublic`, output `public` (**Verified** root `vercel.json`).
3. In **staging project only** (dashboard or future committed staging config — **not edited in this phase**), set rewrite destination to:

   `https://<STAGING_BACKEND_HOST>/api/:match*`

   Placeholder — obtain host from Railway after staging backend deploy.

4. **Do not** connect production Vercel project to staging backend.
5. Disable or avoid production URL in staging project custom domains.

**Conceptual future file (do not create now):** `docs/vercel.staging.json.example` or `vercel.staging.json` used **only** by staging Vercel project — rewrite target = staging Railway.

**Verification without customer impact:**

- Open staging site → login with **test account only**.
- DevTools → Network → any XHR/fetch to `/api/...` → confirm host is **staging** Railway, **not** `automan20-production`.
- Optional: `GET https://<staging-frontend>/api/actuator/health` and compare response headers/body to direct staging backend call.

**Status:** **REQUIRES APPROVAL** for Vercel project creation; production files **unchanged**.

---

## 5. Backup and restore safety requirements

**Verified (repo):** Scripts read `.env.railway.backup` (override via `ENV_FILE`). Restore requires `CONFIRM=YES`. Backup refuses to overwrite existing file; runs `gzip -t` and min size check.

### 5.1 Gaps (Phase 1 + Phase 2)

| Safeguard | Current state | Future script change? |
|-----------|---------------|---------------------|
| Explicit env selection (staging vs production) | **Missing** | **Recommendation:** `AUTOMAN_BACKUP_ENV=staging` required; refuse if unset |
| Target DB identity confirmation | Prints host/db name only | Add mandatory `EXPECTED_DATABASE_NAME` / `EXPECTED_ENV_LABEL` match |
| Refuse restore if identity unverified | **Missing** | Compare host against allowlist file (gitignored) |
| Refuse restore to production | **Missing** | Block if `AUTOMAN_BACKUP_ENV=production` without second confirm |
| Separate staging backup directory | Default `backups/railway/` | **Recommendation:** `backups/railway-staging/` when env=staging |
| Backup integrity | **READY** (`gzip -t`, min bytes) | Optional: checksum sidecar |
| Metadata (timestamp, env label, MySQL version, db name) | Partial (filename timestamp + label) | **Recommendation:** write `.meta.json` beside dump (no credentials) |
| Restore confirmation shows environment | Shows host/db | Add env label + “NOT PRODUCTION” assertion |
| Prevent loading prod creds for staging ops | **Operator process** | Separate `.env.railway.staging.backup` gitignored |
| No overwrite of backup | **READY** | Keep |

**Status:** Using scripts on staging **BLOCKED** until safeguards implemented **or** strict operator checklist approved (**REQUIRES APPROVAL**).

**Operator checklist (interim — Recommendation):**

1. Use dedicated `.env.railway.staging.backup` never shared with prod.
2. Before restore: triple-check Railway dashboard service name = staging.
3. `CONFIRM=YES` only after verbal/written confirmation of target.

---

## 6. Integration and secret isolation

**Verified (repo):** No `@Scheduled` jobs found in backend. Integrations driven by configuration and service calls.

| Integration | Production risk | Staging recommendation | Required setting |
|-------------|-----------------|------------------------|------------------|
| Resend email (`RESEND_API_KEY`, `EmailService`) | Signup/admin mail to real addresses | Omit key or use Resend test + test inbox | `RESEND_API_KEY` unset or test; `APP_MAIL_ADMIN_EMAIL` = test address |
| Gmail SMTP (`GMAIL_*`, `GmailTransportMailService`) | Rixo transport email to customers | Omit credentials | Leave `GMAIL_*` empty |
| Verification links (`APP_VERIFICATION_BASE_URL`) | Prod signup links | Staging API base | Staging backend URL + `/api` |
| Frontend links (`APP_FRONTEND_URL`) | Prod UI in emails | Staging Vercel URL | Staging frontend URL |
| Cloudflare R2 (`R2_*`, `automan.media.r2`) | Writes to prod bucket | Disable or separate bucket | `R2_ENABLED=false` or staging bucket creds |
| MySQL | Data corruption / PII | Staging DB only | Staging `SPRING_DATASOURCE_*` |
| JWT/session auth | Cross-env token reuse | Separate DB + separate frontend origin | Staging-only users |
| PDF generation (local) | Low external risk | OK on staging | N/A |
| External webhooks | **NOT VERIFIED** in repo grep | N/A unless discovered in dashboard | **NOT VERIFIED** |

---

## 7. Human setup checklist (implementation phase — not executed)

### A. Infrastructure creation

- [ ] **REQUIRES APPROVAL:** Create Railway staging project (name recorded).
- [ ] Add `automan-staging-mysql`; note MySQL version and SQL mode.
- [ ] Add `automan-staging-backend`; link to staging MySQL variables only.
- [ ] Set backend variables (Section 3.B); **no prod secrets**.
- [ ] **NOT VERIFIED:** Confirm private networking / service references in dashboard.
- [ ] Review health check path `/api/actuator/health` and timeout ≥ migration estimate.

### B. Frontend creation

- [ ] **REQUIRES APPROVAL:** Create Vercel staging project.
- [ ] Configure staging-only `/api` rewrite to staging backend (**Section 4**).
- [ ] Assign staging domain; **do not** use production domain.
- [ ] Confirm production Vercel project unchanged.
- [ ] Verify Network tab (Section 4.3).

### C. Backup preparation

- [ ] **REQUIRES APPROVAL** for any prod data copy.
- [ ] Prefer synthetic/sanitized data if possible.
- [ ] After restore (if any): `gzip -t`, row counts, Flyway history.
- [ ] Restore **only** with staging env file + identity checklist.

### D. V93 / V94 validation

1. Deploy **`ac72656`** → verify V93 (SQL + smoke in Phase 1 doc).
2. Backup staging (`BACKUP_LABEL=post-v93-staging`).
3. Deploy **`2f03ad4`** → verify V94.
4. Full smoke checklist (Phase 1 doc).

**Status:** All steps **REQUIRES APPROVAL** — not performed.

---

## 8. Rollback and recovery considerations

**Verified (repo + Phase 1 rollback audit):**

1. **V94-successful DB + Commit 1 artifact:** **Not safe** — Flyway validate-on-migrate fails (V94 in history, V94 script absent from Commit 1 JAR).
2. **Hibernate vs Flyway:** Separate checks; both run at startup under `docker,prod`.
3. **MySQL DDL:** Implicit commits; failed migration may leave partial columns/indexes.
4. **`flyway repair`:** Not automatic in prod profile; **never** run blindly — understand failure first.
5. **Backup before V94 on staging:** **Mandatory Recommendation** — only recovery for bad V94 without prod impact.
6. **Production rollback plan:** Must be documented separately; staging success **does not** imply prod rollback to Commit 1 after V94.

### Compatibility matrix

| DB state | App | Startup | Recovery |
|----------|-----|---------|----------|
| Pre-V93 | Pre–Commit 1 | Safe | — |
| Pre-V93 | Commit 1 / 2 | Blocked (missing columns) | Migrate forward |
| V93 OK | Commit 1 | Safe | — |
| V93 OK | Commit 2 | Blocked (no norm column) | Deploy V94 |
| V94 OK | Commit 2 | Safe | — |
| V94 OK | Commit 1 | **Blocked (Flyway)** | Restore to V93 or keep Commit 2 |
| Failed V93/V94 | Any | **Blocked** | Repair after fix or restore backup |
| V94 partial DDL | Any | Blocked | Restore or repair + Commit 2 redeploy |

---

## 9. Risks and unresolved questions

| Risk | Severity | Status |
|------|----------|--------|
| Staging frontend hits production API | High | **BLOCKED** until separate Vercel project |
| Restore script targets production | High | **BLOCKED** until env guards or checklist |
| Migration exceeds health timeout | Medium | **NOT VERIFIED** (Railway settings) |
| Flyway failed migration on staging | Medium | Manual repair/restore playbook required |
| Prod data on staging | Legal/ops | **REQUIRES APPROVAL** |
| Railway Oct 2026 incident / build availability | Ops | **NOT VERIFIED** — see `docs/railway-security-incident-response.md` |
| Portal release checklist stale (V85–V91 vs V92–V94) | Low | Update during implementation (**Recommendation**) |

---

## 10. Explicit approvals required before implementation

1. Create Railway staging project + MySQL + backend.
2. Create Vercel staging project + staging API rewrite.
3. Populate staging secrets (datasource, URLs) — ops only, not in git.
4. Copy or restore production data into staging (if chosen).
5. Run backup/restore scripts against staging (with safeguards).
6. Deploy Commit 1 then Commit 2 to staging.
7. Push commits to remote / CI (if deploy pipeline requires it).

---

## 11. Actions not performed

- No Railway or Vercel creation or configuration changes
- No production or staging database access
- No SQL, Flyway, backup, or restore execution
- No secret creation or printing
- No changes to `vercel.json`, application code, or scripts
- No git commit or push
- Only this document added under `docs/`

---

## Appendix — Files inspected (Phase 2)

- `docs/staging-v93-v94-readiness-plan.md`
- `backend/Dockerfile`
- `backend/build.gradle.kts`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-docker.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/kotlin/com/automan/backend/config/FlywayDockerRepairConfig.kt`
- `backend/src/main/kotlin/com/automan/backend/service/EmailService.kt`
- `vercel.json`
- `public/automan/vercel.json`
- `scripts/backup-railway-mysql.sh`
- `scripts/restore-railway-mysql.sh`
- `.env.railway.backup.example`
- `docs/railway-mysql-backup.md`
- Git read-only state
