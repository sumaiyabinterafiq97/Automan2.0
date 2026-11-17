# Memon corporate website integration (handoff)

## What changed

- **Production homepage source:** [`homepage/index.html`](../homepage/index.html) — client corporate site with staff sign-in (`/automan/login`) and JA/EN i18n for `nav_staff`.
- **Backups:** [`homepage/archive/`](../homepage/archive/) — placeholder (`placeholder-2026/`) and verbatim client file (`memon_corporate_website.source.html`).
- **Build:** `prepareVercelPublic` copies `homepage/index.html` → `public/index.html`, optional `homepage/assets/*` → `public/` (e.g. `logo.jpg`), and only copies `home.css` if that file still exists under `homepage/`.

## Routing (Vercel — unchanged)

| Path | Served by |
|------|-----------|
| `/` | Static corporate `public/index.html` |
| `/automan/*` | Automan Kotlin/JS SPA (`public/automan/`) |
| `/api/*` | Rewrite to Railway (`vercel.json`) |

Automan app code and backend were **not** modified for this integration.

## Client limitations (v1)

1. **Contact form** — shows a thank-you message only; does not send email or call the Automan API. Wire to Resend or a backend endpoint in a follow-up if required.
2. **CDN dependencies** — Tailwind CDN, Google Fonts, Lucide (`unpkg.com`), and Unsplash images load on each visit.
3. **`logo.jpg`** — not in repo; HIRA MART blocks fall back to inline SVG when the image is missing. Add `homepage/assets/logo.jpg` and rebuild to serve at `/logo.jpg`.

## Docker vs Vercel

- **Vercel** uses the `public/` layout from `prepareVercelPublic` (corporate at `/`, Automan at `/automan/`).
- **Docker frontend** ([`docker/Dockerfile.frontend.prod`](../docker/Dockerfile.frontend.prod) + [`docker/nginx/nginx-prod.conf`](../docker/nginx/nginx-prod.conf)) still serves **only** the Automan SPA at `/`. Do not use that image alone as a drop-in for memon.co.jp’s split routing unless nginx is updated separately.

## Deploy checklist (when approved for production)

1. `./gradlew jsBrowserProductionWebpack prepareVercelPublic`
2. Confirm `public/automan/index.html` contains `<base href="/automan/">`.
3. Vercel preview: `/`, `/automan/login`, `/api/actuator/health` (via rewrite).
4. No DNS changes required if the domain already points at the root Vercel project.

## Optional assets

Place files in `homepage/assets/` (e.g. `logo.jpg`). The Gradle task copies each file to the root of `public/` on build.