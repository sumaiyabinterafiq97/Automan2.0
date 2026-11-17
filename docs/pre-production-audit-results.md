# Pre-production audit results (executed)

**Date:** 2026-11-17  
**Scope:** Build verification, static/output checks, live API proxy on `memon.co.jp`, local static smoke. **No production deploy, no config/backend changes.**

---

## 1. Build results

**Command:** `./gradlew clean jsBrowserProductionWebpack prepareVercelPublic`  
**Result:** **BUILD SUCCESSFUL** in **47s** (21 tasks, 18 executed)

**Console confirmations:**

- `✅ Copied Automan build to public/automan/`
- `✅ Copied homepage to public/`
- Webpack size warning: `automan-car-purchase.js` ~2.18 MiB (pre-existing)

**Output verification:**

| Artifact | Status |
|----------|--------|
| `public/index.html` | Present; **identical** to `homepage/index.html` |
| `public/automan/index.html` | `<base href="/automan/">` |
| `public/automan/automan-car-purchase.js` | ~2.3 MB |
| `public/automan/styles.css` | ~303 KB |
| Helper JS + images + fonts under `public/automan/` | Present |
| `public/logo.jpg` | **Missing** (optional client asset) |
| `public/home.css` | Not generated (expected) |

---

## 2. Routes and expected behavior (Vercel)

| Route | Expected behavior |
|-------|-------------------|
| `/` | Static corporate `public/index.html` |
| `/automan` | 302 → `/automan/login` |
| `/automan/login` | SPA shell; client routes to login UI |
| `/automan/purchase` | SPA; requires `authToken` in localStorage for route guard |
| `/automan/*` (other) | Rewrite → `/automan/index.html` |
| `/api/*` | Proxy → `https://automan20-production.up.railway.app/api/*` |

---

## 3. Missing assets

- **`logo.jpg`** at site root (HIRA MART) — 404; inline SVG fallback via `onerror`
- Corporate page depends on **third-party CDN**: Tailwind, Google Fonts, Lucide, Unsplash (not in `public/`)

---

## 4. API connectivity findings

| Check | URL | HTTP | Body (truncated) |
|-------|-----|------|------------------|
| Railway direct | `https://automan20-production.up.railway.app/api/actuator/health` | **200** | `{"status":"UP"}` |
| Via Vercel proxy (live domain) | `https://memon.co.jp/api/actuator/health` | **200** | `{"status":"UP"}` |

**Conclusion:** Existing `vercel.json` API rewrite to Railway is **working** on the live domain. Backend reachability **verified** (health only; no credentials used).

---

## 5. Preview / live smoke (no new deploy)

**Note:** No branch-specific `*.vercel.app` preview URL was available in the repo. Tests used **built `public/` locally** and **read-only requests** to current `memon.co.jp` (still serving **placeholder** homepage until the next Vercel deploy).

### Local static server (`public/`)

| Request | Result |
|---------|--------|
| `GET /` | 200; corporate HTML contains staff `signInLink` |
| `GET /automan/index.html` | 200; `base href="/automan/"` |
| `GET /automan/styles.css` | 200 |
| `GET /automan/login` | **404** without SPA rewrite (expected locally; Vercel rewrite handles this) |
| `GET /logo.jpg` | **404** |

### Live `memon.co.jp` (pre-deploy snapshot)

| Request | Result |
|---------|--------|
| `GET /` | 200; title still **placeholder** (“Memon — Automotive Solutions”) |
| `GET /automan/login` | 200 |
| `GET /api/actuator/health` | 200 `{"status":"UP"}` |

**After you push and get a Vercel preview:** re-run corporate checks on `PREVIEW_HOST/` (expect corporate title `有限会社メモン | MEMON CO., LTD.`).

---

## 6. Authentication findings (staff link)

**Homepage behavior** (`homepage/index.html`):

- **No `authToken`:** Staff links navigate to `/automan/login`.
- **`authToken` present in `localStorage`:** Click handler `preventDefault` → `window.location.href = '/automan/purchase'`.
- **Invalid/expired token:** Same as token present — homepage does **not** call the backend. User may reach `/automan/purchase` shell; API calls fail until **Logout** (clears token + `POST /api/auth/logout`).

**Automan (unchanged):**

- Route guard: `hasAuthToken()` only (`AppRouter.kt`).
- `installAuthenticatedFetch`: **401 does not auto-clear** token (`AuthSetup.kt`).
- **Backend** validates Bearer token on protected `/api/*` — localStorage alone is **not** proof of session.

### Manual preview checklist (auth)

1. Logged out → Staff → `/automan/login`.
2. Valid login → Staff from `/` → purchase list loads data.
3. Set bogus `authToken` in DevTools → Staff → purchase shell with API errors → Logout → login again.

---

## 7. Deployment risks (unchanged)

- Vercel previews use repo `vercel.json` → **production Railway** API unless overridden in dashboard.
- `public/automan/vercel.json` must not be used as a separate root project.
- Docker nginx frontend ≠ Vercel split routing for corporate + Automan.
- CDN + missing `logo.jpg` (low impact with SVG fallback).
- Optimistic staff redirect with stale token (low–medium UX).

---

## 8. Vercel preview testing instructions

1. Push branch with `homepage/`, `build.gradle.kts`, and built `public/` (if committed).
2. Open Vercel **Preview** URL for that deployment.
3. Verify: `/` (corporate JA/EN), `/automan` → login, `/automan/login` assets 200, `/api/actuator/health` 200.
4. Run auth checklist in section 6.
5. Promote to production only after client sign-off.

**Do not deploy to production** until preview checklist passes.