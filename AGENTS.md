# AGENTS.md

## Cursor Cloud specific instructions

### Architecture
Automan is a car purchase management system with:
- **Frontend**: Kotlin/JS (IR compiler) compiled to JavaScript, served by nginx on port 8080
- **Backend**: Kotlin/JVM Spring Boot 3.2 on port 8083 (context path `/api`)
- **Database**: MySQL 8.0 on port 3306

### Running services

1. **MySQL** (Docker): `sudo docker start automan_mysql` — listens on port 3306. Database `automan_car_purchase`, user `automan_user`, password `automan_password`.

2. **Backend**: `cd /workspace/backend && SPRING_JPA_HIBERNATE_DDL_AUTO=update ./gradlew bootRun` — The `SPRING_JPA_HIBERNATE_DDL_AUTO=update` env var is **required** because the SQL migrations create columns with slightly different types than what Hibernate entities expect (e.g., `TEXT` vs `TINYTEXT` for `@Lob` fields, `DECIMAL` vs `FLOAT` for `Double` fields). The default profile uses `validate` which fails. The Docker profile (`application-docker.yml`) already uses `update`.

3. **Frontend build**: `cd /workspace && ./gradlew jsBrowserDevelopmentWebpack` — outputs to `build/dist/js/developmentExecutable/`.

4. **Frontend server** (nginx): `sudo nginx` — serves frontend on port 8080 and proxies `/api/` to the backend. Config at `/etc/nginx/sites-available/automan`.

### Database migration ordering gotcha
The SQL migration `01-init-multiplatform.sql` adds a FK constraint referencing the `clients` table, which is created later in `10-clients-table.sql`. When setting up from scratch, run migrations in this order: `10-clients-table.sql` first, then `01-*`, `02-*`, `03-*`, `04-*`, `11-*`, `12-*`.

### Build commands
- Backend build: `cd /workspace/backend && ./gradlew build -x test`
- Frontend build: `cd /workspace && ./gradlew jsBrowserDevelopmentWebpack`
- Backend compile check: `cd /workspace/backend && ./gradlew compileKotlin`
- Frontend compile check: `cd /workspace && ./gradlew compileKotlinJs`

### Known issues
- Backend and frontend tests have pre-existing compilation errors (illegal characters in test names, val reassignment, conflicting imports). Tests do not compile as of the current state.

### Default credentials
- **Login**: `admin@automan.com` / `admin123`

### Gradle wrapper
The repository does not include `gradle-wrapper.jar` files. The update script regenerates them using a system-installed Gradle. Both projects need the wrapper: root project (Gradle 8.7) and backend (Gradle 8.10).
