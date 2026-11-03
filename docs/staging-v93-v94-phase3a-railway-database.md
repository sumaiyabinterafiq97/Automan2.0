# Phase 3A — Railway staging MySQL (Automan2.0)

**Objective:** Isolated Railway staging project + MySQL only (no backend, no migrations, no prod data).

**References:** [`staging-v93-v94-readiness-plan.md`](staging-v93-v94-readiness-plan.md), [`staging-v93-v94-phase2-architecture.md`](staging-v93-v94-phase2-architecture.md)

**Repository HEAD (verified at start/end):** `2f03ad4c718f015633dc58545d09b8d7bb2e0777` on `master`

**Agent execution date:** 2026-10-30

---

## A. Execution summary

| Item | Result |
|------|--------|
| Railway CLI available | **No** (`railway` not found on PATH) |
| Railway dashboard access from agent | **No** — not available in this environment |
| Staging project `automan-staging` created by agent | **No** |
| Staging MySQL `automan-staging-mysql` created by agent | **No** |
| Staging connection / identity SQL executed by agent | **No** |

**Phase 3A verdict (agent):** **BLOCKED** — infrastructure must be created manually in the Railway dashboard (or after installing/authenticating Railway CLI locally), then this document updated with non-sensitive details.

**Phase 3A verdict (human, after manual steps):** Mark **COMPLETE** in Section B below once checklist in Section H is done and isolation checks pass.

---

## B. Infrastructure details (fill after manual creation)

| Field | Value |
|-------|--------|
| Railway project name | `automan-staging` (target) |
| Railway project ID | _NOT CREATED — record from dashboard URL_ |
| Environment name | _e.g. production vs staging env in Railway_ |
| MySQL service name | `automan-staging-mysql` (target) |
| MySQL service ID | _NOT CREATED_ |
| MySQL version (`SELECT VERSION()`) | _NOT VERIFIED — expect 8.0.x_ |
| Database name (`SELECT DATABASE()`) | _Railway default often `railway` — record actual_ |
| Persistent volume attached | _NOT VERIFIED_ |
| Service health (dashboard) | _NOT VERIFIED_ |
| Internal connection variables available | _NOT VERIFIED (`MYSQLHOST`, etc.)_ |

**Do not paste passwords, tokens, JDBC URLs, or TCP proxy credentials into this file.**

---

## C. Isolation verification

**Agent-performed checks:** None (no Railway access).

**Required checks after you create staging (human — use dashboard + optional read-only SQL):**

| # | Check | How | Expected | Result |
|---|--------|-----|----------|--------|
| 1 | Project is not production | Dashboard: project name/ID ≠ production Automan project | Separate project `automan-staging` | ☐ |
| 2 | MySQL service lives only in staging project | Service list under `automan-staging` only | No prod MySQL linked | ☐ |
| 3 | Credentials are new | Variables tab shows Railway-generated values, not copied from prod | New staging secrets | ☐ |
| 4 | Storage is staging-only | MySQL service has its own volume; not prod volume | Staging volume | ☐ |
| 5 | No prod hostname in staging project | Search Variables for prod hostnames | None | ☐ |
| 6 | Service healthy | Railway MySQL service status | Healthy / running | ☐ |
| 7 | MySQL version recorded | Read-only SQL below | 8.0.x compatible | ☐ |

**Permitted read-only SQL** (only after connecting to **staging** MySQL with staging credentials via Railway Query tab, `mysql` CLI to **staging TCP proxy**, or Railway internal shell — **never production**):

```sql
SELECT VERSION();
SELECT DATABASE();
SELECT @@GLOBAL.sql_mode;
SELECT @@SESSION.sql_mode;
```

**Optional identity check (no schema changes):**

```sql
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = DATABASE();
```

On a **new empty** Railway MySQL, table count may be small (system only). **Do not** import dumps or run Flyway in Phase 3A.

---

## D. Configuration status

| Component | Status |
|-----------|--------|
| Railway staging project | **NOT CONFIGURED** (pending manual create) |
| Staging MySQL service | **NOT CONFIGURED** |
| Staging backend service | **Intentionally omitted** (Phase 3B) |
| `SPRING_DATASOURCE_*` on backend | **Not set** (no backend) |
| Flyway / V93 / V94 | **Not run** |
| Vercel staging | **Not in scope** (later phase) |
| Backup/restore to staging | **Not performed** |

---

## E. Blockers

1. **Railway CLI not installed** in the agent environment — cannot run `railway login`, `railway init`, or `railway add` non-interactively without user setup.
2. **No Railway API/dashboard automation** available to the agent — cannot confirm account auth or existing projects.
3. **Billable resource creation** — creating a project/MySQL may require an active Railway plan; user must confirm in dashboard.
4. **MySQL version** — if Railway offers only non-8.0 templates, **stop** and document available versions before proceeding (do not pick incompatible version silently).

---

## F. Explicit not-performed list

Confirmed for this agent run:

- [x] No backend service created or deployed
- [x] No Flyway migrations run
- [x] No backup or restore
- [x] No production Railway/MySQL access attempted
- [x] No production data copied
- [x] No application source, Docker, `vercel.json`, or script changes
- [x] No secrets written to the repository
- [x] No `.env` file with credentials created
- [x] No git commit or push

---

## G. Manual Railway dashboard procedure (Phase 3A)

Perform these steps **yourself** in [Railway](https://railway.com). Use a browser logged into the **correct** account. **Do not** open the production Automan project when creating staging.

### Step 1 — Inspect existing resources

1. List projects. Note production project name/ID for **avoidance** only (do not open prod MySQL).
2. Check whether **`automan-staging`** already exists.
   - If it exists and contains a healthy staging-only MySQL, you may **reuse** after verifying isolation (Section C).
   - If name exists but purpose is unclear, **do not reuse** until identity is confirmed.

### Step 2 — Create project (if needed)

1. **New Project** → name: **`automan-staging`**.
2. Confirm billing/plan prompt if shown.
3. Record **project ID** (from URL or settings) in Section B — not secrets.

### Step 3 — Add MySQL service

1. Inside **`automan-staging`**, **Add service** → **MySQL** (or Database → MySQL per Railway UI).
2. Name service **`automan-staging-mysql`** if the UI allows renaming.
3. Prefer **MySQL 8.0** if version is selectable; if not, note offered versions and stop if not 8.x compatible.
4. Ensure a **persistent volume** is attached (default for Railway MySQL plugin — verify in service settings).
5. Wait until service status is **healthy**.

### Step 4 — Record non-sensitive metadata

From the MySQL service **Variables** tab (do **not** copy values into git):

- Note **database name** variable (often `MYSQLDATABASE` / `MYSQL_DATABASE`).
- Confirm variables are **generated for this service**, not imported from production.

### Step 5 — Isolation verification

Complete Section C checklist.

Run permitted read-only SQL against **staging only**.

Update Section B with version, database name, volume, and health.

### Step 6 — Stop

Do **not** add a backend service, link GitHub repo, or deploy Docker in this phase.

---

## H. Optional: Railway CLI for a future session (local machine)

Not executed by agent. Install only if you want CLI-based management **after** confirming CLI project targeting does not touch production.

```bash
# Example only — run locally after reading Railway CLI docs
# brew install railway
# railway login
# railway list
# Create/link ONLY automan-staging — verify project name before every command
```

Never run `railway link` in the production project while performing staging work.

---

## I. Next phase (after approval)

**Phase 3B:** Create **`automan-staging-backend`**, set staging-only `SPRING_DATASOURCE_*` and `SPRING_PROFILES_ACTIVE=docker,prod`, deploy pinned image — **still no production**.

---

## J. Git verification

| Check | Before agent run | After agent run |
|-------|------------------|-----------------|
| Branch | `master` | `master` |
| HEAD | `2f03ad4…` | `2f03ad4…` |
| Index (staged) | empty | empty |
| New file from Phase 3A | — | `docs/staging-v93-v94-phase3a-railway-database.md` only |
| Application code | unchanged | unchanged |

Other untracked docs (`staging-v93-v94-readiness-plan.md`, `phase2-architecture.md`) preserved. Unrelated `.gradle` / PDF / tmp unchanged by this phase.

**No commit. No push.**
