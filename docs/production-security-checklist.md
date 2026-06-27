# Production Security Checklist

Use this checklist before deploying the backend to Fly and the frontend to Vercel.

## Fly Backend Secrets

Set sensitive backend values as Fly secrets, not in `fly.toml`.

```bash
fly secrets set \
  SPRING_PROFILES_ACTIVE=prod \
  DATABASE_URL='jdbc:postgresql://...' \
  DB_USERNAME='...' \
  DB_PASSWORD='...' \
  JWT_SECRET='generate-a-long-random-secret-at-least-32-chars' \
  SUPABASE_URL='https://your-project.supabase.co' \
  SUPABASE_KEY='your-server-side-supabase-key' \
  SUPABASE_SIGNED_URL_EXPIRATION='3600' \
  TOYYIBPAY_SECRET_KEY='...' \
  TOYYIBPAY_CATEGORY_CODE='...' \
  TOYYIBPAY_RETURN_URL='https://your-vercel-domain/payment-status' \
  TOYYIBPAY_CALLBACK_URL='https://your-fly-app.fly.dev/api/webhooks/toyyibpay' \
  TOYYIBPAY_VERIFY_CALLBACK='true' \
  CORS_ORIGINS='https://your-vercel-domain,https://your-custom-domain' \
  SETUP_ADMIN_ENABLED='false'
```

Only set `SETUP_ADMIN_ENABLED=true` temporarily while creating the first admin, and set a strong `SETUP_ADMIN_SECRET` when you do.

## Vercel Frontend Environment

Set this in Vercel Production environment variables:

```bash
NEXT_PUBLIC_API_URL=https://your-fly-app.fly.dev/api
```

Do not expose backend-only secrets in Vercel variables prefixed with `NEXT_PUBLIC_`.

## Supabase Storage

Create or convert the `polaroid-glossy` bucket as private. Customer photos must not use public bucket URLs.

The backend stores image keys like:

```text
orders/{orderId}/original/{uuid}.jpg
```

The backend returns signed URLs for viewing/downloading files. Signed URLs are short-lived and controlled by:

```text
SUPABASE_SIGNED_URL_EXPIRATION=3600
```

## Required Production Checks

- Confirm `/api/files/upload` requires login.
- Confirm customer file access is limited to the order owner or staff roles.
- Confirm `/api/auth/setup-admin` returns 404 when `SETUP_ADMIN_ENABLED=false`.
- Confirm ToyyibPay callbacks are rejected when the hash, bill code, amount, or order reference is wrong.
- Confirm `CORS_ORIGINS` contains only the Vercel/custom production domains.
- Confirm the Supabase bucket is private and public object URLs do not work.
