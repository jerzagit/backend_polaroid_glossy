# Frontend Handoff: Backend Production Security Controls

This document summarizes the backend security changes that the frontend project must account for.

Backend branch:

```text
feat/production-security-hardening
```

Backend commit:

```text
9d69f20 feat: harden production security controls
```

Frontend project currently discussed:

```text
/Users/jerza/personal/polaroid_glossy_backup/
```

## API Base URL

The frontend should call the Spring Boot backend directly.

Local:

```bash
NEXT_PUBLIC_BACKEND_API_BASE=http://localhost:8080/api
```

Production on Vercel:

```bash
NEXT_PUBLIC_BACKEND_API_BASE=https://your-fly-app.fly.dev/api
```

## Auth Headers

The following backend endpoints now require backend JWT authentication:

```http
POST /api/orders
POST /api/orders/{orderNumber}/pay
POST /api/files/upload
GET /api/files/order/{orderId}
```

Frontend requests must include:

```http
Authorization: Bearer <backend-jwt>
```

The existing frontend `backendRequest()` helper should attach this header for authenticated backend calls.

## Image Upload Flow

Image upload is no longer public.

Old flow:

```text
Upload photos first using random checkoutId
Create order after upload
Store uploaded public URLs
```

This no longer works.

New backend expectation:

```text
Order must already exist
Upload must be authenticated
Upload orderId must be a real backend order id or order number
Only order owner or staff can upload/list files
```

Backend storage path:

```text
orders/{orderId}/original/{uuid}.jpg
```

Upload response:

```json
{
  "key": "orders/{orderId}/original/{uuid}.jpg",
  "url": "temporary-signed-url",
  "fileName": "uuid.jpg"
}
```

Important:

```text
Store key, not url.
```

`url` is temporary because Supabase Storage is now expected to be private.

Recommended integration decision:

```text
Create backend order first, then upload photos, then attach returned keys to order items.
```

The current backend stores `imageUrls` into `OrderItem.s3Keys`, but the safest final flow may need a backend endpoint to attach image keys after order creation.

## Supabase Storage

The Supabase bucket should be private:

```text
polaroid-glossy
```

The backend now returns signed URLs instead of public object URLs.

Signed URL expiry is configured by backend env:

```bash
SUPABASE_SIGNED_URL_EXPIRATION=3600
```

Frontend should not assume image URLs are permanent.

## Public Order Lookup / Tracking

Unauthenticated order tracking now requires email verification.

Old call:

```http
GET /api/orders/{orderNumber}
```

New unauthenticated call:

```http
GET /api/orders/{orderNumber}?email=customer@example.com
```

If the user is authenticated and owns the order, the email query is not required.

Frontend tracking UI should ask for:

```text
Order number
Customer email
```

## ToyyibPay

ToyyibPay bill creation should go through the Spring backend:

```http
POST /api/orders/{orderNumber}/pay
```

The backend returns:

```json
{
  "billCode": "...",
  "paymentUrl": "https://toyyibpay.com/...",
  "orderNumber": "..."
}
```

ToyyibPay callback URL should point to Fly backend:

```text
https://your-fly-app.fly.dev/api/webhooks/toyyibpay
```

ToyyibPay return URL should point to Vercel frontend:

```text
https://your-vercel-domain/payment-status
```

Legacy frontend routes should not be used for payment processing:

```text
src/app/api/toyyibpay/create-bill/route.ts
src/app/api/toyyibpay/callback/route.ts
```

The backend now verifies ToyyibPay callback data before marking an order paid:

```text
order reference
bill code
amount
callback hash
```

## Setup Admin

The setup admin endpoint is disabled by default outside local dev.

Production default:

```bash
SETUP_ADMIN_ENABLED=false
```

If it must be temporarily enabled:

```bash
SETUP_ADMIN_ENABLED=true
SETUP_ADMIN_SECRET=strong-random-secret
```

Disable it again after creating the first admin.

## Env Ownership

Keep these in Vercel:

```bash
NEXT_PUBLIC_BACKEND_API_BASE
NEXTAUTH_SECRET
NEXTAUTH_URL
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
```

Keep these in Fly backend secrets:

```bash
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
SUPABASE_URL
SUPABASE_KEY
SUPABASE_SIGNED_URL_EXPIRATION
TOYYIBPAY_SECRET_KEY
TOYYIBPAY_CATEGORY_CODE
TOYYIBPAY_RETURN_URL
TOYYIBPAY_CALLBACK_URL
TOYYIBPAY_VERIFY_CALLBACK=true
CORS_ORIGINS
SETUP_ADMIN_ENABLED=false
```

Do not expose backend-only secrets through `NEXT_PUBLIC_` variables.

## Backend Production Checklist

Before going live:

```text
Supabase bucket is private
CORS_ORIGINS only includes Vercel/custom production domains
ToyyibPay callback points to Fly backend
ToyyibPay return URL points to Vercel frontend
SETUP_ADMIN_ENABLED=false
Frontend sends backend JWT for protected calls
Frontend tracking requires order number + email
Frontend stores Supabase keys, not signed URLs
```
