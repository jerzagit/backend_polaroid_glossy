# Quick Start — Polaroid Glossy Backend

Spring Boot 3.4.1 REST API on Java 17 with PostgreSQL. Production runs on Fly.io
(`polaroid-glossy-backend`, region `sin`); the Next.js app in `frontend/` deploys
to Vercel separately.

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | `pom.xml` sets `<java.version>17</java.version>`, `.java-version` = 17 |
| Maven | 3.9.x | must resolve as `mvn` on `PATH` (see wrapper note) |
| PostgreSQL | 15+ | 17 in production (Supabase-hosted) |
| Docker | optional | only for the throwaway Postgres in step 2 |

**Do not use `./mvnw` or `mvnw.cmd`.** The checked-in wrapper is broken on
Windows — it quotes the classpath twice, so every command dies with
`ClassNotFoundException: org.apache.maven.wrapper.MavenWrapperMain`. Use `mvn`
from a local Maven 3.9.x install, or reuse the distribution the wrapper already
downloaded:

```bash
# git-bash: put the cached Maven 3.9.6 on PATH for this shell
export PATH="$(echo "$HOME"/.m2/wrapper/dists/apache-maven-3.9.6-bin/*/apache-maven-3.9.6/bin):$PATH"
mvn -v     # expect 3.9.x on Java 17 (`java -version`)
```

## 2. Create the dev database

The `dev` profile connects to `jdbc:postgresql://localhost:5433/polaroid`
(user `postgres`, password `password` by default). It is **5433, not 5432**, so it
can coexist with another local Postgres.

```bash
# Docker
docker run -d --name polaroid-postgres-dev -p 5433:5432 \
  -e POSTGRES_PASSWORD=password -e POSTGRES_DB=polaroid postgres:15

# or an existing local server
createdb -h localhost -p 5433 -U postgres polaroid
```

No schema step: dev runs Flyway **disabled** with
`spring.jpa.hibernate.ddl-auto=update`, so Hibernate builds the tables on boot.

## 3. Configuration

Nothing auto-loads `.env` files — values come from environment variables or the
profile YAML. `.env.example` is a reference template; export the variables in
your shell or set them in the IDE run configuration. Note its `DATABASE_URL`
points at port 5432 — the dev profile defaults to 5433.

Dev needs **no** environment variables; all of these have defaults:

| Variable | Dev default |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/polaroid` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `password` |
| `JWT_SECRET` | dev-only 32+ char secret |
| `SETUP_ADMIN_ENABLED` / `SETUP_ADMIN_SECRET` | `true` / `admin-secret-2024` |

Dev also mocks the integrations: uploads go to `.dev-storage/`, payments are
mock-approved, and mail targets `localhost:1025` (MailHog/Mailpit).

`prod` has **no defaults** for these — the app will not start without them:
`DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`,
`TOYYIBPAY_SECRET_KEY`, `TOYYIBPAY_CATEGORY_CODE`, `TOYYIBPAY_RETURN_URL`,
`TOYYIBPAY_CALLBACK_URL`, `STORAGE_R2_ACCOUNT_ID`, `STORAGE_R2_API_TOKEN`.

Optional in prod (default): `SERVER_PORT` (8080), `PUBLIC_API_URL`,
`FRONTEND_URL`, `STORAGE_PROVIDER` (r2), `STORAGE_R2_BUCKET`,
`STORAGE_R2_S3_ENDPOINT`, `STORAGE_R2_REGION` (auto), `STORAGE_R2_ACCESS_KEY`,
`STORAGE_R2_SECRET_KEY`, `STORAGE_R2_PATH_STYLE` (true),
`PAYMENT_EXPIRATION_HOURS` (24), `PAYMENT_REMINDER_HOURS` (14),
`SETUP_ADMIN_ENABLED` (false), `SETUP_ADMIN_SECRET`,
`TOYYIBPAY_VERIFY_CALLBACK` (true), `SPRING_MAIL_HOST` / `_PORT` /
`_USERNAME` / `_PASSWORD`.

`SUPABASE_URL`, `SUPABASE_KEY`, `SUPABASE_SIGNED_URL_EXPIRATION` are **legacy**:
still bound in the base/uat config and in `fly.toml` env, but production storage
is Cloudflare R2 (`app.storage.provider=r2`). Leave them empty for new work.

## 4. Profiles

| Profile | Port | DB / schema | Storage & mail |
|---|---|---|---|
| `dev` (default) | 8080 | Postgres 5433, Flyway off, `ddl-auto=update` | mock storage `.dev-storage`, mock payments, mail `localhost:1025` |
| `uat` | 8080 | `DATABASE_URL`, Flyway on, `ddl-auto=validate` | no defaults — supply storage/DB env |
| `prod` | `SERVER_PORT` (8080) | Flyway on, `baseline-on-migrate=true`, `baseline-version=2`, `ddl-auto=validate` | R2, SMTP via `SPRING_MAIL_*`, CORS strictly from `CORS_ORIGINS` |

`application.yml` sets `spring.profiles.active=dev`, so a bare run is dev.

## 5. Run

```bash
mvn spring-boot:run                                    # dev (default profile)
mvn spring-boot:run -Dspring-boot.run.profiles=uat     # needs DATABASE_URL etc.
```

Or `./scripts/run-backend-dev.sh` — pins JDK 17, starts `polaroid-postgres-dev`
via Docker if missing, and runs the dev profile.

Look for `Started PolaroidApplication in X.XXX seconds`, then the API is at
`http://localhost:8080`.

## 6. Smoke test

```bash
curl http://localhost:8080/api/health
# {"status":"ok"}

curl -X POST "http://localhost:8080/api/auth/setup-admin?secret=admin-secret-2024" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"admin123","name":"Admin"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"admin123"}'
```

`setup-admin` returns 404 unless `app.setup-admin.enabled` is true (it is in dev)
and 401 on a wrong `secret`.

API docs (springdoc; both paths are `permitAll` in `SecurityConfig`):

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## 7. Tests

```bash
mvn test
```

62 tests across 12 classes, all green, no database needed — the Spring context
tests run on in-memory H2 in PostgreSQL mode (`MODE=PostgreSQL`). CI runs
`mvn -q compile` then `mvn test` on every push and PR to `main`.

## 8. Deploy to production

Automatic path: push to `main` → `.github/workflows/ci.yml` → on success
`.github/workflows/fly-deploy.yml` deploys that exact commit with
`flyctl deploy --remote-only`. A red build cannot reach production;
`workflow_dispatch` can trigger a deploy manually.

Manual path from a clean checkout, with `flyctl` on `PATH` and either
`FLY_API_TOKEN` set or `flyctl auth login` done:

```bash
scripts/deploy-prod.sh --check    # preflight: flyctl, fly.toml, creds, branch, unpushed commits
scripts/deploy-prod.sh            # deploy HEAD, then poll /api/health for up to 150s
```

That script runs `flyctl deploy --remote-only --config fly.toml --app polaroid-glossy-backend`.
The app runs in `sin`, minimum 2 machines, 512 MB, health check `GET /api/health`.

Secrets live in Fly, never in git:

```bash
flyctl secrets set JWT_SECRET='<long-random>' \
  DATABASE_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require' \
  --app polaroid-glossy-backend
```

Migrations: 30 Flyway files (`V1`..`V29`, including `V19_1`) in
`src/main/resources/db/migration`. Prod applies them at boot with
`baseline-on-migrate=true` / `baseline-version=2`, so an existing pre-baseline
schema counts as applied through V2. Dev never runs them. Full secret list:
`docs/fly-backend-deployment.md`.

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| `ClassNotFoundException: MavenWrapperMain` | `mvnw.cmd` is broken; use `mvn` |
| `Connection refused` at startup | Postgres down or wrong port — dev expects 5433 |
| `database "polaroid" does not exist` | create it (step 2) |
| `Port 8080 already in use` | `SERVER_PORT=8081 mvn spring-boot:run` |
| Prod storage/auth errors | R2 env vars missing |
| Prod schema validation failure | migration missing; never hand-edit the prod schema |
