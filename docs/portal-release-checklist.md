# Portal release checklist

This builds images for the commit you check out. It does not push images, deploy, or connect to RDS.

Do not run `scripts/update-aws-docker.sh` for this release. That script still pushes `:latest`.

## Before any image is started against a database

Confirm `flyway_schema_history` on a restored copy, not on RDS, already ends at version 84. This release applies V85–V91 on startup. If that history table is missing, the production profile baselines at version 1 and can replay older destructive migrations.

`SPRING_PROFILES_ACTIVE=docker,prod` validates the schema. Hibernate does not use `ddl-auto=update`. Flyway applies pending versions.

## Build from the release commit

```bash
git checkout <release-commit>
export AUTOMAN_IMAGE_TAG="$(git rev-parse --short=12 HEAD)"
export DOCKER_PLATFORM=linux/amd64

./gradlew jsBrowserProductionWebpack --offline
docker build --platform "$DOCKER_PLATFORM" \
  -f docker/Dockerfile.frontend.prod \
  -t "automan-frontend:${AUTOMAN_IMAGE_TAG}" \
  .

cd backend
./gradlew bootJar -x test --offline
docker build --platform "$DOCKER_PLATFORM" \
  -f Dockerfile.prebuilt \
  -t "automan-backend:${AUTOMAN_IMAGE_TAG}" \
  .
```

The frontend Dockerfile has no auto-login build argument. After the webpack build, the production bundle must not contain the seeded admin password, `admin@automan.com`, or `automan-prod-auto-login`.

## Tags

Use the same 12-character commit for both images:

- `automan-frontend:${AUTOMAN_IMAGE_TAG}`
- `automan-backend:${AUTOMAN_IMAGE_TAG}`

`docker-compose.hub.yml` and `docker-compose.prod.yml.example` require `AUTOMAN_IMAGE_TAG`. They do not pull `:latest`.

## Not ready to deploy

These values are still unset on the production host until someone confirms them there:

- `APP_FRONTEND_URL` — public site origin
- `APP_VERIFICATION_BASE_URL` — public API origin ending in `/api`
- `RESEND_API_KEY` and `APP_MAIL_ADMIN_EMAIL` if approval mail is used
- `GMAIL_USERNAME` and `GMAIL_APP_PASSWORD` if Rixo mail is used
- `R2_ENABLED` and the R2 account, key, secret, bucket, and endpoint if pictures are used

The health check is `GET /api/actuator/health`. The production profile hides health details. Nginx reaches the backend through `host.docker.internal`, and the production Compose files add that host alias.
