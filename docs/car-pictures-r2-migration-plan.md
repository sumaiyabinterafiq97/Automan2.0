# Car Pictures → Cloudflare R2 Migration Plan

**Status:** Plan only — no code changes yet  
**Date:** 2026-07-07  
**Scope:** Move Automan 2.0 car pictures from base64-in-MySQL to Cloudflare R2 + `purchase_media` metadata table

---

## 1. Executive summary

Automan currently stores car pictures as **base64 data URLs** inside `purchases.extended_attributes.carPictures` (JSON). This bloats Railway MySQL, slows purchase load/save, and inflates backups.

**Recommended target:**

| Layer | Technology |
|-------|------------|
| File bytes | **Cloudflare R2** (S3-compatible object storage) |
| Metadata | **MySQL `purchase_media` table** (Flyway migration) |
| API | Spring Boot multipart upload + presigned read URLs |
| Frontend | Replace base64 `collectCarPictures()` flow with upload-to-R2 |

**Estimated cost for ~1,000 purchases:** **$0–1/month** on R2 (within or just above free tier).

**Rollout strategy:** Phased, backward-compatible — legacy base64 remains readable until migrated.

---

## 2. Current state (as-is)

### 2.1 Storage

| Item | Detail |
|------|--------|
| Column | `purchases.extended_attributes` JSON |
| Key | `carPictures` |
| Format | JSON string of `[{ id, data }]` where `data` is `data:image/jpeg;base64,...` |
| Legacy column | `car_pictures` TEXT — **dropped in V51** |
| Registry note | `V41__purchase_field_registry.sql`: *"Or separate purchase_media table if blobs grow"* |

### 2.2 Code touchpoints (do not change yet — reference for implementation)

**Backend**

| File | Role |
|------|------|
| `PurchaseExtendedAttributesService.kt` | Reads/writes `carPictures` in JSON |
| `PurchaseService.kt` | `updatePurchasePartial` serializes `carPictures` to JSON string |
| `PurchaseChangeHistoryService.kt` | Diffs `carPictures` by char length / full string |
| `PurchaseExportService.kt` | Exports `carPictures` as string column |
| `Purchase.kt` | `@Transient carPictures` + `extendedAttributesJson` |

**Frontend**

| File | Role |
|------|------|
| `MinimalPurchaseApp.kt` | `handleCarPictureUpload` (FileReader → base64) |
| `MinimalPurchaseApp.kt` | `collectCarPictures()` / `loadExistingCarPictures()` |
| `PurchaseManagement.kt` | Column visibility includes `carPictures` |

### 2.3 Constraints already in UI

- Max **5 MB** per image
- Multiple images per purchase
- Accepts `image/*` only

### 2.4 Problems to solve

1. MySQL + backup bloat (especially Railway `.sql.gz` dumps)
2. Slow `GET /purchases` if images ever leak into list payloads
3. `JSON.stringify` / edit-form failures on large base64 (code already comments on this)
4. Change history stores useless megabyte-scale diffs
5. No CDN, no efficient caching, no proper file lifecycle

---

## 3. Target architecture

```
┌─────────────┐     multipart        ┌──────────────┐     PutObject      ┌─────────────┐
│   Browser   │ ──────────────────►  │ Spring Boot  │ ─────────────────► │ Cloudflare  │
│  (Add/Edit) │                      │   API        │                    │     R2      │
└─────────────┘                      └──────┬───────┘                    └─────────────┘
       ▲                                    │
       │ presigned GET URL                   │ INSERT metadata
       └────────────────────────────────────┼──────────────► ┌──────────────────┐
                                             │                 │ purchase_media   │
                                             └────────────────►│ (MySQL/Railway)  │
                                                               └──────────────────┘
```

### 3.1 Design principles

1. **DB stores references, not bytes** — only `file_key`, `content_type`, `size`, `sort_order`
2. **Purchase list API never includes image bytes** — optional `mediaCount` or thumbnail URL only
3. **Backward compatible** — read legacy `carPictures` JSON until migration completes
4. **Private by default** — presigned URLs (15–60 min expiry), not public bucket
5. **Chassis in object key** — `purchases/{chassis}/{uuid}.{ext}` for ops/debugging
6. **Flyway-first** — all schema via migration (next: `V58__purchase_media.sql`)

---

## 4. Database design

### 4.1 New table: `purchase_media`

```sql
CREATE TABLE purchase_media (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_id     BIGINT NOT NULL,
    chassis         VARCHAR(100) NOT NULL,
    file_key        VARCHAR(512) NOT NULL,
    original_name   VARCHAR(255) NULL,
    content_type    VARCHAR(64) NOT NULL,
    file_size       INT UNSIGNED NOT NULL,
    sort_order      SMALLINT NOT NULL DEFAULT 0,
    storage_provider ENUM('R2') NOT NULL DEFAULT 'R2',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(120) NULL,
    deleted_at      TIMESTAMP NULL,
    CONSTRAINT fk_purchase_media_purchase
        FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
    CONSTRAINT uk_purchase_media_file_key UNIQUE (file_key),
    INDEX idx_purchase_media_purchase_id (purchase_id),
    INDEX idx_purchase_media_chassis (chassis),
    INDEX idx_purchase_media_purchase_sort (purchase_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 4.2 What stays in `extended_attributes`

| Phase | `carPictures` in JSON |
|-------|------------------------|
| Phase 1–2 | Unchanged (legacy read/write continues) |
| Phase 3 | Migrated purchases: key removed or set to `null` |
| Phase 4 | Key deprecated; documentation only |

### 4.3 Optional later: `purchase_field_registry` row

Update registry note from `MIGRATE` → `MIGRATED` after Phase 4.

---

## 5. Cloudflare R2 setup (infrastructure — no app code)

### 5.1 Account & bucket

1. Create Cloudflare account (free tier sufficient to start)
2. Create R2 bucket: `automan-car-media` (or `automan-car-media-prod`)
3. Enable **object versioning** (recommended for accidental delete recovery)
4. Create **API token** with Object Read & Write for this bucket only

### 5.2 Environment variables (Railway backend service)

| Variable | Example | Notes |
|----------|---------|-------|
| `R2_ACCOUNT_ID` | Cloudflare account ID | Required |
| `R2_ACCESS_KEY_ID` | API token access key | Required |
| `R2_SECRET_ACCESS_KEY` | API token secret | Required |
| `R2_BUCKET_NAME` | `automan-car-media-prod` | Required |
| `R2_ENDPOINT` | `https://<account_id>.r2.cloudflarestorage.com` | S3-compatible |
| `R2_PUBLIC_BASE_URL` | (empty for private) | Only if using public CDN later |
| `MEDIA_PRESIGNED_URL_TTL_SECONDS` | `3600` | Default 1 hour |

### 5.3 Object key convention

```
purchases/{chassis}/{media_uuid}.{ext}

Example:
purchases/ABC-1234567/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg
```

- Sanitize chassis for path (replace `/`, spaces)
- UUID prevents collisions on re-upload
- Extension from validated content type (not user filename)

### 5.4 CORS (R2 bucket settings)

Allow `PUT`/`GET` from Automan frontend origin(s):

- `http://localhost:8080` (dev)
- Production Railway frontend URL

Or proxy uploads through backend only (simpler security — **recommended for v1**).

---

## 6. API design (new endpoints)

Base path proposal: `/api/purchases/{purchaseId}/media`

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/purchases/{id}/media` | List media metadata + presigned URLs |
| `POST` | `/purchases/{id}/media` | Upload one file (`multipart/form-data`) |
| `PUT` | `/purchases/{id}/media/order` | Reorder `sort_order` |
| `DELETE` | `/purchases/{id}/media/{mediaId}` | Soft-delete row + delete R2 object |
| `GET` | `/purchases/{id}/media/{mediaId}/url` | Fresh presigned URL (optional) |

### 6.1 Upload validation (server-side)

| Rule | Value |
|------|-------|
| Max file size | 5 MB (match current UI) |
| Allowed types | `image/jpeg`, `image/png`, `image/webp`, `image/gif` |
| Max files per purchase | 20 (configurable) |
| Auth | Same as existing purchase endpoints |

### 6.2 Purchase GET behavior change (important)

| Endpoint | Today | Target |
|----------|-------|--------|
| `GET /purchases` (list) | No `carPictures` in practice | **Never** include media bytes |
| `GET /purchases/{id}` | Full `carPictures` base64 JSON | **Phase 1:** unchanged. **Phase 2+:** add `media: [...]` with URLs; legacy `carPictures` only if not migrated |
| `GET /purchases/chassis/{chassis}` | Same | Same |

### 6.3 Dependencies to add (when implementing)

- `software.amazon.awssdk:s3` (AWS SDK v2 — works with R2 S3-compatible API)

No new dependency until Phase 2 implementation.

---

## 7. Frontend changes (when implementing)

### 7.1 Add / Edit purchase form

| Current | Target |
|---------|--------|
| `FileReader` → base64 in DOM `data-picture-data` | Upload file to `POST .../media` immediately or on save |
| `collectCarPictures()` → JSON in purchase PUT body | Send only `mediaIds` / order, or upload completes independently |
| `loadExistingCarPictures()` parses base64 | Load from `GET .../media` URLs into `<img src="presigned-url">` |

### 7.2 Backward compatibility in UI

`loadExistingCarPictures()` should:

1. Try `GET /purchases/{id}/media` first
2. If empty, fall back to legacy `carPictures` base64 in purchase payload
3. Show badge "Legacy photos — will migrate on next save" (optional)

### 7.3 Purchase list / export

- List view: show `📷 3` count icon (from `mediaCount`) — no image load
- Excel export: export URLs or count, not base64

---

## 8. Migration strategy (existing data)

### 8.1 Pre-migration audit (run on Railway before any code deploy)

```sql
-- How many purchases have car pictures in JSON?
SELECT COUNT(*) AS purchases_with_pictures
FROM purchases
WHERE extended_attributes IS NOT NULL
  AND JSON_EXTRACT(extended_attributes, '$.carPictures') IS NOT NULL
  AND TRIM(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures'))) NOT IN ('', '[]', 'null');

-- Total approximate payload size
SELECT
  SUM(CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(extended_attributes, '$.carPictures')))) AS total_chars
FROM purchases
WHERE JSON_EXTRACT(extended_attributes, '$.carPictures') IS NOT NULL;
```

Record results in `docs/diagnostics/car-pictures-baseline.txt`.

### 8.2 Migration job (Phase 3)

**Option A — Admin script (recommended v1)**

- Kotlin CLI or secured admin endpoint `POST /api/admin/migrate-car-pictures?batch=50`
- For each purchase with legacy `carPictures`:
  1. Parse JSON array
  2. Decode base64 → bytes
  3. Upload to R2
  4. Insert `purchase_media` rows
  5. Remove `carPictures` from `extended_attributes`
  6. Log success/failure per purchase

**Option B — SQL + external script**

- Export base64 payloads, process offline, less ideal

### 8.3 Migration safety rules

| Rule | Reason |
|------|--------|
| **Never auto-delete** legacy JSON until R2 upload verified | Data integrity |
| **Idempotent** — skip if `purchase_media` rows already exist for purchase | Re-runnable |
| **Dry-run mode** first | Count files, estimate R2 size |
| **Backup before batch** | `./scripts/backup-railway-mysql.sh` |
| **Do not run on production without staging test** | ERP safety |

### 8.4 Rollback

| Scenario | Action |
|----------|--------|
| Migration partially failed | Re-run idempotent job; legacy JSON still present if not cleared |
| R2 upload wrong files | Delete `purchase_media` rows + R2 objects; legacy JSON untouched if not cleared |
| Full rollback of feature | Disable new upload endpoints; UI reads legacy only; drop table only with explicit approval |

---

## 9. Implementation phases & gates

### Phase 0 — Planning & baseline (this document)

- [x] Document current architecture
- [ ] Run DB audit queries on Railway
- [ ] Create R2 bucket + credentials (infra only)
- [ ] Store R2 secrets in Railway (not in git)
- [ ] Sign-off: owner approves plan

**Gate:** Baseline size known; R2 bucket exists; no code merged yet.

---

### Phase 1 — Schema only (low risk)

**Deliverables:**

- Flyway `V58__purchase_media.sql`
- JPA entity `PurchaseMedia` + repository
- No API exposure yet; no behavior change

**Gate:**

- Flyway applies cleanly on local Docker + Railway staging
- Existing app works unchanged
- `purchase_media` table empty

---

### Phase 2 — Upload & read API (behind feature flag)

**Deliverables:**

- `R2StorageService` (S3 client)
- `PurchaseMediaService` + `PurchaseMediaController`
- Feature flag: `automan.media.r2.enabled=false` by default
- Integration tests with LocalStack or mock S3
- Purchase GET optionally includes `media[]` when flag on

**Gate:**

- Upload/download works in staging with flag on
- Purchase list still fast (no bytes in list)
- Legacy `carPictures` still works when flag off

---

### Phase 3 — Frontend switch (staging)

**Deliverables:**

- New upload UI path (multipart)
- `loadExistingCarPictures` dual-read (R2 + legacy)
- Stop sending base64 in PUT body for new uploads
- `collectCarPictures` deprecated path

**Gate:**

- New purchase: photos in R2 only
- Edit old purchase: legacy photos still visible
- Save purchase without re-uploading all images

---

### Phase 4 — Data migration (production)

**Deliverables:**

- Admin migration job
- Dry-run report
- Batch migrate with backup per batch
- Clear `carPictures` from JSON after verify

**Gate:**

- 100% of purchases with pictures have `purchase_media` rows
- Random sample: image in R2 matches legacy
- DB size reduction measurable
- `mysqldump` backup size reduced

---

### Phase 5 — Cleanup

**Deliverables:**

- Remove legacy base64 write path
- Simplify `loadExistingCarPictures` (R2 only)
- Update `PurchaseChangeHistoryService` to log `mediaCount` not char length
- Update export column to URL/count
- Update `purchase_field_registry` note

**Gate:**

- No `carPictures` key in `extended_attributes` for active purchases
- Documentation updated
- 30-day monitoring: no missing image reports

---

## 10. Security

| Topic | Approach |
|-------|----------|
| Bucket access | **Private** — no public read |
| URL access | Presigned GET, short TTL |
| Upload auth | Require authenticated user + purchase edit permission |
| File validation | Magic-byte check, not just `Content-Type` header |
| Path traversal | Sanitize chassis; server generates `file_key` |
| Secrets | Railway env vars only; never commit |
| Delete | Soft-delete in DB + hard-delete in R2 (or lifecycle rule) |

---

## 11. Testing plan

| Test | Type |
|------|------|
| Upload JPEG/PNG/WebP under 5 MB | Integration |
| Reject > 5 MB | Integration |
| Reject non-image | Integration |
| List media returns presigned URLs | Integration |
| Delete removes R2 object | Integration |
| Purchase DELETE cascades `purchase_media` | Integration |
| Legacy base64 still loads on edit | E2E |
| Migration job idempotent | Integration |
| Purchase list response size unchanged | Performance |
| 10 concurrent uploads | Load (staging) |

---

## 12. Cost estimate (1,000 purchases)

| Scenario | Storage | Monthly cost |
|----------|---------|--------------|
| Typical (5 × 2 MB) | ~10 GB | **$0** (free tier) |
| Heavy (8 × 3 MB) | ~24 GB | **~$0.21** |
| Max (10 × 5 MB) | ~50 GB | **~$0.60** |

Operations (reads/writes) remain within free tier for normal ERP usage.

---

## 13. Risks & mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Data loss during migration | Low | Keep legacy JSON until verified; backup first |
| Presigned URL expiry breaks open edit tab | Medium | Refresh URLs on load; longer TTL for edit session |
| R2 outage | Low | Legacy fallback until Phase 5; retry upload |
| Increased complexity | Medium | Phased rollout + feature flag |
| CORS upload issues | Medium | v1: proxy upload through backend only |
| Railway env misconfiguration | Medium | Staging validation checklist |

---

## 14. Effort estimate (rough)

| Phase | Effort |
|-------|--------|
| Phase 0 — Baseline + R2 setup | 0.5–1 day |
| Phase 1 — Schema | 0.5 day |
| Phase 2 — Backend API + R2 | 2–3 days |
| Phase 3 — Frontend | 2–3 days |
| Phase 4 — Migration job + prod run | 1–2 days |
| Phase 5 — Cleanup | 1 day |
| **Total** | **~7–10 days** |

---

## 15. What we are NOT doing in this plan

- No code changes until Phase 0 gate sign-off
- No dropping `carPictures` from `extended_attributes` until migration verified
- No public R2 bucket (unless explicitly decided later)
- No Cloudinary/imgix (unnecessary for v1)
- No storing images in Railway volumes
- No storing BLOBs in MySQL `purchase_media`

---

## 16. Recommended next steps (your approval)

1. **Review this plan** — confirm R2 + phased approach
2. **Run baseline audit SQL** on Railway (read-only)
3. **Create R2 bucket** + Railway env vars (infra only)
4. **Approve Phase 1** — then implement `V58__purchase_media.sql` only
5. Staging deploy → Phase 2 → Phase 3 → migration → cleanup

---

## 17. Decision log

| Date | Decision | By |
|------|----------|-----|
| 2026-07-07 | Cloudflare R2 selected over Railway MySQL/volumes for car pictures | Pending sign-off |
| 2026-07-07 | Phased migration; legacy base64 readable until Phase 4 complete | Pending sign-off |
| 2026-07-07 | v1 uploads proxied through backend (no direct browser→R2) | Pending sign-off |
