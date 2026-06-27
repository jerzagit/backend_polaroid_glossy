# Production Workflow

This is the working process for local development, production preparation, backend deployment, and post-deploy checks.

## Branch Workflow

Use one focused branch per deployable change.

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/<short-clear-name>
```

Before pushing:

```bash
git status --short
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test
cd frontend && npx tsc --noEmit
```

Commit only files related to the change. Leave unrelated dirty files unstaged.

```bash
git add <changed-files>
git commit -m "<clear commit message>"
git push -u origin feature/<short-clear-name>
```

Open a PR to `main`, review the diff, and merge only after the checks and manual test pass.

## Local Development Workflow

Backend:

```bash
cd /Users/jerza/personal/backend_polaroid_glossy
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Seller dashboard:

```bash
cd /Users/jerza/personal/backend_polaroid_glossy/frontend
npm install
npm run dev
```

Customer frontend:

```bash
cd /Users/jerza/personal/polaroid_glossy_backup
npm install
npm run dev
```

Local backend default:

```bash
http://localhost:8080/api
```

## Production Preparation Checklist

Before deploying backend:

- PR is merged to `main`.
- `main` is pulled locally.
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` passes.
- Fly remote build check passes.
- Fly secrets are set.
- Supabase database is reachable.
- Supabase storage bucket exists.
- ToyyibPay production return/callback URLs are correct.
- Frontend domains are included in `CORS_ORIGINS`.

Before deploying frontend:

- Customer frontend points to production backend:

```bash
NEXT_PUBLIC_BACKEND_API_BASE=https://polaroid-glossy-backend.fly.dev/api
```

- Seller dashboard points to production backend:

```bash
NEXT_PUBLIC_API_URL=https://polaroid-glossy-backend.fly.dev/api
```

- Vercel domains are added to backend `CORS_ORIGINS`.

## Backend Deployment Workflow

Backend runs on Fly.io.

Detailed setup:

```text
docs/fly-backend-deployment.md
```

Build only:

```bash
flyctl deploy --remote-only --build-only --app polaroid-glossy-backend
```

Deploy:

```bash
flyctl deploy --app polaroid-glossy-backend
```

Verify health:

```bash
curl https://polaroid-glossy-backend.fly.dev/api/health
```

Expected:

```json
{"status":"ok"}
```

Check status and logs:

```bash
flyctl status --app polaroid-glossy-backend
flyctl logs --app polaroid-glossy-backend
```

## Post-Deploy Smoke Test

Run these after every backend deploy:

- `GET /api/health` returns `{"status":"ok"}`.
- Login works from seller dashboard.
- Overview loads.
- Orders list loads.
- Order filters work for date range, month, week, status, email, phone, and order ID.
- Customer checkout creates an order.
- Customer photo upload stores file metadata.
- Seller dashboard can see uploaded order images.
- ToyyibPay redirect works.
- ToyyibPay callback updates payment status.

## Rollback

List releases:

```bash
flyctl releases --app polaroid-glossy-backend
```

Rollback:

```bash
flyctl releases rollback <version> --app polaroid-glossy-backend
```

Check health and logs after rollback:

```bash
curl https://polaroid-glossy-backend.fly.dev/api/health
flyctl logs --app polaroid-glossy-backend
```

## Secrets Rule

Never commit real secrets.

Use Fly secrets for backend production values:

```bash
flyctl secrets set KEY='value' --app polaroid-glossy-backend
```

Use Vercel project environment variables for frontend values.

## Current Known Gaps

- ToyyibPay production secrets still need to be set.
- Frontend production deployment will be handled later on Vercel.
- Customer frontend currently has unrelated TypeScript issues in auth/reviews that may block Vercel if type-check is enforced.
- `frontend/package-lock.json` in the backend repo is locally dirty and intentionally not part of current deploy work.
