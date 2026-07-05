# Fly Backend Deployment

This project uses Fly.io for the Spring Boot backend. The customer frontend and seller dashboard can point to this backend API once deployed.

For the full release workflow, see `docs/production-workflow.md`.

## Current Fly App

- App: `polaroid-glossy-backend`
- Region: `sin`
- Runtime: Java 17
- VM: `shared-cpu-1x`, `512 MB`
- Internal port: `8080`
- Health check: `/api/health`
- Public API base after deploy: `https://polaroid-glossy-backend.fly.dev/api`

## Deployment Files

- `Dockerfile` builds the Spring Boot jar with Maven and runs it on Eclipse Temurin Java 17.
- `.dockerignore` keeps frontend, local storage, env files, target output, and docs out of the Docker build context.
- `fly.toml` configures the Fly app, region, port, production Spring profile, and health check.
- `.github/workflows/fly-deploy.yml` deploys `main` to Fly.io with `flyctl deploy --remote-only`.
- `HealthController` exposes `GET /api/health`.

## Required Fly Secrets

Set secrets in Fly. Do not commit real values into git.

```bash
flyctl secrets set \
  DATABASE_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require' \
  DB_USERNAME='postgres' \
  DB_PASSWORD='<password>' \
  JWT_SECRET='<long-random-secret>' \
  SUPABASE_URL='https://<project-ref>.supabase.co' \
  SUPABASE_KEY='<supabase-service-role-key>' \
  TOYYIBPAY_SECRET_KEY='<toyyibpay-secret>' \
  TOYYIBPAY_CATEGORY_CODE='<toyyibpay-category>' \
  TOYYIBPAY_RETURN_URL='https://<frontend-domain>/payment-status' \
  TOYYIBPAY_CALLBACK_URL='https://polaroid-glossy-backend.fly.dev/api/webhooks/toyyibpay' \
  CORS_ORIGINS='https://<frontend-domain>,https://<seller-dashboard-domain>' \
  --app polaroid-glossy-backend
```

Already staged in Fly during setup:

- `DATABASE_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `SUPABASE_URL`
- `SUPABASE_KEY`
- `CORS_ORIGINS`

Still needed before production payment flow is ready:

- `TOYYIBPAY_SECRET_KEY`
- `TOYYIBPAY_CATEGORY_CODE`
- `TOYYIBPAY_RETURN_URL`
- `TOYYIBPAY_CALLBACK_URL`

## Required GitHub Secret

For automatic deploys from GitHub Actions, add this repository secret:

```text
FLY_API_TOKEN
```

Generate it locally with:

```bash
flyctl tokens create deploy --app polaroid-glossy-backend
```

Then add the value in GitHub under:

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

## Build Check

Run a remote build without deploying:

```bash
flyctl deploy --remote-only --build-only --app polaroid-glossy-backend
```

This confirms the Dockerfile can build the Spring Boot jar on Fly builders.

## Deploy

Deploys run automatically when `main` receives a push. You can also trigger the `Deploy to Fly` workflow manually from GitHub Actions.

For a local manual deploy after all secrets are set:

```bash
flyctl deploy --app polaroid-glossy-backend
```

If secrets were staged before the first deployment, Fly applies them during deploy.

Keep the backend machine at 512 MB or higher:

```bash
flyctl scale memory 512 --app polaroid-glossy-backend
```

The first production deploy reused an existing Supabase schema. Keep future schema changes in new Flyway migration files and let Flyway apply them during deploy.

## Verify

Check health:

```bash
curl https://polaroid-glossy-backend.fly.dev/api/health
```

Expected:

```json
{"status":"ok"}
```

Check app status:

```bash
flyctl status --app polaroid-glossy-backend
```

Check logs:

```bash
flyctl logs --app polaroid-glossy-backend
```

## Frontend Env Later

When deploying the customer frontend, set:

```bash
NEXT_PUBLIC_BACKEND_API_BASE=https://polaroid-glossy-backend.fly.dev/api
```

When deploying the seller dashboard, set:

```bash
NEXT_PUBLIC_API_URL=https://polaroid-glossy-backend.fly.dev/api
```

Also update `CORS_ORIGINS` on Fly to include the real frontend domains.
