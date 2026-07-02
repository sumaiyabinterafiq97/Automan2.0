# Railway MySQL backup (first 3 months)

Railway offers **two** backup approaches. Use **both** for production safety.

## Option A — Railway native backups (in-dashboard)

Works on **Hobby** for MySQL services with a volume.

1. Railway → **MySQL service** → **Backups** tab.
2. **Edit schedule** (top right) and enable:
   - **Monthly** — kept **3 months** (matches the 3-month retention goal).
   - Optional: **Weekly** — kept 1 month (extra safety).
3. Click **Create backup** anytime for a manual snapshot.

| Pros | Cons |
|------|------|
| One-click restore in Railway | **Cannot download** as `.sql` |
| Scheduled, no local tooling | Restore only to **same project + environment** |
| Incremental volume snapshots | Uses volume storage (billed) |

**Restore (native):** Backups tab → select backup → **Restore**.

---

## Option B — Downloadable SQL dump (`mysqldump`)

Portable file you can store locally, send to client, or restore elsewhere.

### One-time setup

```bash
brew install mysql-client
cp .env.railway.backup.example .env.railway.backup
# Edit .env.railway.backup with Railway MySQL public TCP proxy credentials
chmod +x scripts/backup-railway-mysql.sh scripts/restore-railway-mysql.sh
```

Get credentials: **MySQL service → Variables** (use public host/port from TCP proxy, not `mysql.railway.internal`).

### Backup (download)

```bash
./scripts/backup-railway-mysql.sh
# Optional label: BACKUP_LABEL=monthly-jun ./scripts/backup-railway-mysql.sh
```

Output: `backups/railway/railway-YYYYMMDD-HHMMSS.sql.gz` (gitignored).

### Re-import (restore)

```bash
# Take a fresh backup before overwriting production
./scripts/backup-railway-mysql.sh

CONFIRM=YES ./scripts/restore-railway-mysql.sh backups/railway/railway-20260623-120000.sql.gz
```

After restore, restart the **backend** service so Flyway/app state matches the DB.

---

## Recommended schedule (first 3 months)

| When | Action |
|------|--------|
| **Now** | Enable Railway **Monthly** backup schedule + run one **manual** native backup |
| **Now** | Run `./scripts/backup-railway-mysql.sh` and store `.sql.gz` off-machine (Drive, client) |
| **Before each deploy** | SQL dump with `BACKUP_LABEL=pre-deploy-...` |
| **Monthly** | New `mysqldump` + confirm native monthly snapshot exists |

---

## Local Docker (dev stack)

```bash
docker exec automan_mysql_multiplatform mysqldump -uautoman_user -pautoman_password \
  automan_car_purchase | gzip > backups/local/automan-$(date +%Y%m%d).sql.gz
```

Restore locally:

```bash
gunzip -c backups/local/automan-YYYYMMDD.sql.gz | \
  docker exec -i automan_mysql_multiplatform mysql -uautoman_user -pautoman_password automan_car_purchase
```

---

## Notes

- Native snapshots are **volume-level**; `mysqldump` is **logical SQL** (better for audits and client handover).
- Deleting/wiping the Railway volume **deletes native backups** — keep off-site `.sql.gz` copies.
- Public TCP proxy egress may incur small Railway network charges on dump/restore.
