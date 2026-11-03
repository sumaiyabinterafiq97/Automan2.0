# Railway security incident — Automan2.0 response runbook

Operational checklist for the Oct 2026 Railway env-var security disclosure. Complements [railway-mysql-backup.md](railway-mysql-backup.md). **Does not rotate credentials for you** — use Railway dashboard and provider consoles.

## Status snapshot (agent-checked)

| Source | As of | Summary |
|--------|--------|---------|
| [status.railway.com](https://status.railway.com) | 2026-10-22 ~05:00 UTC | **Major outage** — Enterprise builds/redeploy available; Pro/Hobby/Free still rolling out. Railway Public API tokens invalidated. Pre-deploy commands may stall on some services. Phase 2 (MySQL rotate + redeploy) when **your** plan can build. |
| [Security disclosure](https://blog.railway.com/p/security-disclosure-wednesday-october-21st) | Oct 21, 2026 | Treat Railway Variables as potentially exposed; rotate secrets and review logs. |
| [Rotate MySQL](https://blog.railway.com/p/security-disclosure-recommendation-rotate-mysql-database-credentials) | — | Required for Automan (MySQL service). |

**Subscribe:** incident updates on the status page (email/RSS per Railway UI).

---

## Repo verification (no live secrets in git)

Automated scan of this repository:

| Check | Result |
|-------|--------|
| OpenAI / Anthropic / OpenRouter references | **None** — skip AI provider dashboards unless you manually added keys in Railway |
| `.env` tracked in git | **No** — `.env` is gitignored; only `.env.example` / `.env.railway.backup.example` are tracked |
| Production secrets in source | **None found** — only test placeholders (`test-secret`, docs, examples) |

Optional env vars the app reads (set only in Railway, not in repo): see [application-docker.yml](../backend/src/main/resources/application-docker.yml) — `SPRING_DATASOURCE_*`, `RESEND_API_KEY`, `R2_*`, `GMAIL_*`, `APP_*`.

---

## Production safety (read before changing anything)

- **Phase 1 (read-only):** status, variable **names**, logs, optional backup — **does not** stop live Automan or delete data.
- **Do not** revoke Resend/R2/Gmail keys at the provider until **after** new keys are in Railway and you **redeploy**.
- **MySQL password:** rotate only in a window where you can update app variables and **redeploy/restart** immediately after.
- Rotation changes **credentials**, not table data. Optional backup: `./scripts/backup-railway-mysql.sh` (needs local `.env.railway.backup` — see [railway-mysql-backup.md](railway-mysql-backup.md)).

---

## Phase 1 — Now (while builds may be paused)

### 1. Incident links

- [ ] [status.railway.com](https://status.railway.com)
- [ ] [Security disclosure](https://blog.railway.com/p/security-disclosure-wednesday-october-21st)
- [ ] [Rotate MySQL credentials](https://blog.railway.com/p/security-disclosure-recommendation-rotate-mysql-database-credentials)

### 2. Variable inventory (names only — fill in Railway dashboard)

**Automan2.0 service**

| Variable name | Set? (Y/N) | Secret? | Notes |
|---------------|------------|---------|-------|
| `SPRING_DATASOURCE_URL` or `${{MySQL.*}}` ref | | Yes | Required |
| `SPRING_DATASOURCE_USERNAME` | | Yes | |
| `SPRING_DATASOURCE_PASSWORD` | | Yes | |
| `SPRING_PROFILES_ACTIVE` | | No | e.g. `docker,prod` |
| `PORT` | | No | |
| `APP_FRONTEND_URL` | | No | |
| `RESEND_API_KEY` | | If set | Optional signup email |
| `R2_ENABLED` / `R2_*` | | If set | Optional car pictures |
| `GMAIL_*` | | If set | Optional Rixo PDF email |
| `OPENAI_*` / other AI | | If set | Not used by Automan code — rotate only if you added manually |
| Docker/GHCR registry tokens | | If set | Skip if never added |

**MySQL service**

| Variable name | Set? (Y/N) | Notes |
|---------------|------------|-------|
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | | Rotate per Railway MySQL guide |
| Public TCP proxy host/port (for backup script) | | In Variables / Connect tab |

### 3. Skip unless applicable

- [ ] **AI providers** — skip if inventory has no AI keys.
- [ ] **Private registry** — skip if no Docker/GHCR vars.

### 4. Log review (Railway dashboard)

**Automan2.0 service logs** — look for:

- [ ] Spike in `401` / `403` or failed auth
- [ ] Unusual paths or IPs you do not recognize
- [ ] Errors connecting to MySQL (`Communications link failure`, `Access denied`)

**MySQL service logs** — look for:

- [ ] `Access denied` from unknown hosts
- [ ] Connection count spikes

If `RESEND_*` / `R2_*` / `GMAIL_*` are set, check those provider dashboards for abnormal API usage.

### 5. Optional backup (before MySQL password change)

```bash
cp .env.railway.backup.example .env.railway.backup
# Edit with Railway MySQL TCP credentials (never commit this file)
chmod +x scripts/backup-railway-mysql.sh
./scripts/backup-railway-mysql.sh
```

### 6. Billing

- [ ] If usage is near the $20 limit, raise the cap temporarily so redeploy is not blocked when builds return.

---

## Phase 2 — When Railway enables builds for your plan

Confirm on [status.railway.com](https://status.railway.com) before starting.

### MySQL (required)

1. [ ] Optional: run backup script (above).
2. [ ] Follow Railway [MySQL rotation guide](https://blog.railway.com/p/security-disclosure-recommendation-rotate-mysql-database-credentials).
3. [ ] Update **Automan2.0** `SPRING_DATASOURCE_*` (or MySQL references) to match.
4. [ ] **Redeploy** Automan2.0 (e.g. service → Cmd/Ctrl+K → Deploy Latest Commit).
5. [ ] Smoke-test: login, purchase list, edit save.
6. [ ] Ask staff to **log out and sign in again**.

### Resend / R2 / Gmail (only if inventory marked “Set”)

1. [ ] Create **new** key at provider.
2. [ ] Update Railway Variables.
3. [ ] **Redeploy** and test (signup email / car pictures / Gmail PDF).
4. [ ] **Revoke old** key at provider.

### Final

- [ ] Re-create Railway **API token** if you use Railway Public API (old tokens invalidated).
- [ ] Deploy pending app changes (e.g. local fixes) if desired.

---

## Smoke-test checklist (production)

- [ ] Staff login
- [ ] Purchase list load
- [ ] Edit purchase save
- [ ] Car picture upload (if R2 enabled)
- [ ] Signup approval email (if Resend enabled)

---

*Generated for Automan2.0 Railway security response. Update the status table when you check status.railway.com again.*
