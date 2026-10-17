# Frontend Handoff: Backend Production Security Controls

> **Status: updated 2026-10-18.** This supersedes the version written 2026-06-27. The
> backend has moved on since then (guest uploads with tokens, Cloudflare R2 storage,
> 5-minute signed URLs), so several instructions below reverse that version. Every claim
> here was re-verified against the backend source. Items describing the old design are
> labelled **Historical**; the previously wrong "upload requires JWT" claim is corrected
> in [Auth Requirements](#auth-requirements).

Audience: the frontend team. This document lists what the frontend must do to work with
the current backend security model.

## Version Reference

**Historical** (the branch/commit this handoff originally described — it does not reflect
the current API surface, kept for traceability):

```text
feat/production-security-hardening
9d69f20 feat: harden production security controls
```

For current behaviour read the backend source, not that branch: `SecurityConfig`,
`FileController`, `FileService`, `OrderController`, `OrderService`.

## API Base URL

The frontend calls the Spring Boot backend directly.

Local:

```bash
NEXT_PUBLIC_BACKEND_API_BASE=http://localhost:8080/api
```

Production on Vercel:

```bash
NEXT_PUBLIC_BACKEND_API_BASE=https://your-fly-app.fly.dev/api
```

## Auth Requirements

**Correction:** `POST /api/files/upload` is **not** JWT-only. It is `permitAll` in
`SecurityConfig` and guests upload with an upload token (see
[Image Upload Flow](#image-upload-flow)). `POST /api/orders` is also public — it creates a
guest draft. The old table in this document claimed all four endpoints require JWT; it is
**Historical** and reproduced here only so nobody re-applies it:

```text
Historical (incorrect):
POST /api/orders                   -> requires JWT
POST /api/orders/{orderNumber}/pay -> requires JWT
POST /api/files/upload             -> requires JWT
GET /api/files/order/{orderId}     -> requires JWT
```

Current behaviour:

| Endpoint | Auth |
|---|---|
| `POST /api/orders` | Public (guest draft). Send JWT if the user is logged in so the order is attached to their account. |
| `POST /api/files/upload` | Public for guests **only** with `orderId` + `customerEmail` + valid `uploadToken`; logged-in users are authorized by JWT and must own the order or be staff. |
| `POST /api/files/payment-proof` | Requires JWT. |
| `POST /api/orders/{orderNumber}/pay` | Requires JWT. |
| `GET /api/orders/my` | Requires JWT. |
| `GET /api/orders/{orderNumber}` | Public with `?email=` matching the order's customer, or owner JWT, or staff JWT. |
| `GET /api/files/order/{orderId}` | **ADMIN only** — the customer-facing frontend must not call it. |
| `GET /api/files/order/{orderId}/download` and strip export routes | ADMIN / MARKETING / PACKER only. |
| `DELETE /api/files` | ADMIN / MARKETING / PACKER only. |

Authenticated calls send:

```http
Authorization: Bearer ***
```

The existing `backendRequest()` helper should attach this header for authenticated
backend calls. Do not attach it to guest uploads — those are authorized by the upload
token, not by a session.

## Image Upload Flow

Upload is no longer the old fire-and-forget public upload, but it is **not** JWT-only
either. It is order-scoped and token-gated for guests.

**Historical** (this flow no longer works):

```text
Upload photos first using random checkoutId
Create order after upload
Store uploaded public URLs
```

Current backend expectation:

```text
Order must already exist
Guest uploads need orderId + customerEmail + uploadToken
Logged-in uploads are authorized by JWT + order ownership
Every upload targets an existing order item
Upload capacity per item is enforced (extra uploads are rejected, not silently accepted)
```

### Guest flow

1. `POST /api/orders` (no JWT) → `{ success, order }`. The guest order response includes
   `uploadToken`. It is shown **once**: the backend stores only its SHA-256 hash plus an
   expiry (default 168 hours), and clears both automatically once every order item has
   its expected number of images.
2. For each photo, `POST /api/files/upload` as multipart with `file`, `orderId`,
   `customerEmail`, `uploadToken`, `orderItemId` (required when the order has more than
   one item), and optionally `photoId` for idempotent re-uploads.
3. A missing, wrong, or expired token returns **403**; a full item returns **409**
   (`UploadConflict`). Handle them as distinct states — do not retry a 403.

Anonymous uploads are rate-limited to **10 per 10 minutes per client IP + order**; past
that the backend returns **400** ("Too many upload attempts"). Surface it as
"try again shortly", not as a validation error. Files above 25 MB may be refused earlier
by the multipart limit as **413** rather than 400, so treat both as "file too large".

### Logged-in flow

Same call without `uploadToken`, with `Authorization: Bearer ***`. If `customerEmail` is
sent it must still match the order or the call 403s.

### Upload response

```json
{
  "success": true,
  "url": "presigned-url-expiring-in-300s",
  "fileName": "uuid.jpg",
  "orderItemId": "…",
  "uploadedImageCount": 2,
  "expectedImageCount": 3,
  "remainingImageCount": 1
}
```

Important:

```text
Keys are attached server-side. Do not send keys back to the backend.
There is no key field in the response any more.
```

**Historical:** the previous handoff said "store key, not url" and showed a `key` field in
the upload response. That field is no longer returned, and POSTing image keys back with
order items is obsolete — the backend appends keys to the order item itself.

`url` is temporary because the bucket is private (see below).

## Object Storage: Cloudflare R2

Production stores uploads in **Cloudflare R2 through its S3-compatible API**
(`STORAGE_PROVIDER=r2` in `application-prod.yml`). Dev uses the local filesystem; an AWS
S3 strategy also exists.

**Historical:** Supabase Storage bucket `polaroid-glossy` and
`SUPABASE_SIGNED_URL_EXPIRATION=3600` are legacy. Supabase is not part of the upload path
any more; the frontend needs no Supabase configuration or keys.

Key layout:

```text
orders/{orderNumber}/{orderItemId}/{uuid}.jpg
payment-proofs/{orderNumber}/{uuid}.jpg
```

(Not `orders/{orderId}/original/{uuid}.jpg` — that path is **Historical**.)

### Signed URL lifetime: 300 seconds

Every URL the backend mints comes from `FileService` / `OrderMapper` with a 300-second
expiry. Frontend consequences:

- Do not persist image URLs in a database, `localStorage`, or SSR HTML that outlives a
  page view. Store nothing, or store your own reference and re-read the order.
- After ~5 minutes a stale URL fails at R2 (not with a friendly backend error), so
  refresh URLs by re-reading the order before print/checkout screens.
- Long-lived gallery screens should refresh on focus or on an interval.
- Render immediately from the upload response `url`; re-fetch for anything later.

## Public Order Lookup / Tracking

Unauthenticated tracking requires email verification (unchanged from the original
handoff):

```http
GET /api/orders/{orderNumber}?email=customer@example.com
```

The backend returns the order only if the email matches `customerEmail`, or the caller's
JWT owns the order, or the caller is staff. Otherwise **403**.

Tracking UI should ask for:

```text
Order number
Customer email
```

## Do Not Use `GET /api/orders?userId=<uuid>`

`GET /api/orders?userId=…` is public and unchecked on the backend today, and
`GET /api/reviews` publicly exposes customer `userId` values. Using it from the frontend
would spread a known vulnerability:

- Use `GET /api/orders/my` (JWT) for the signed-in customer's order list.
- Use `GET /api/orders/{orderNumber}?email=…` for guest tracking.
- Never read a `userId` out of a review payload and feed it to this endpoint.

The backend fix (removing the `userId` branch, or authorizing it) is outstanding; this
document will be updated when it lands.

## ToyyibPay

Bill creation goes through the Spring backend:

```http
POST /api/orders/{orderNumber}/pay
```

Requires JWT. The backend returns:

```json
{
  "billCode": "...",
  "paymentUrl": "https://toyyibpay.com/...",
  "orderNumber": "..."
}
```

ToyyibPay callback URL points to the Fly backend:

```text
https://your-fly-app.fly.dev/api/webhooks/toyyibpay
```

ToyyibPay return URL points to the Vercel frontend:

```text
https://your-vercel-domain/payment-status
```

Do not route payment processing through the legacy frontend routes:

```text
src/app/api/toyyibpay/create-bill/route.ts
src/app/api/toyyibpay/callback/route.ts
```

The webhook is public but verified server-side in `PaymentService.verifyCallback` (order
reference, bill code, amount in cents, and callback hash must all match;
`TOYYIBPAY_VERIFY_CALLBACK=true` in prod). A rejected callback returns **403**. The
frontend must not trust return-URL query parameters for payment state — read the order.

## Setup Admin

`/api/auth/setup-admin` is `permitAll` in the security config but returns **404** unless
`SETUP_ADMIN_ENABLED=true`; with the flag on, a wrong `secret` returns **401**. Production
default:

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
STORAGE_PROVIDER=r2
STORAGE_R2_ACCOUNT_ID
STORAGE_R2_API_TOKEN
STORAGE_R2_BUCKET
STORAGE_R2_ACCESS_KEY
STORAGE_R2_SECRET_KEY
STORAGE_R2_REGION=auto
STORAGE_R2_PATH_STYLE=true
TOYYIBPAY_SECRET_KEY
TOYYIBPAY_CATEGORY_CODE
TOYYIBPAY_RETURN_URL
TOYYIBPAY_CALLBACK_URL
TOYYIBPAY_VERIFY_CALLBACK=true
CORS_ORIGINS
SETUP_ADMIN_ENABLED=false
```

**Historical:** `SUPABASE_URL`, `SUPABASE_KEY`, `SUPABASE_SIGNED_URL_EXPIRATION` still
exist as unused properties; do not add them to new environments.

Do not expose backend-only secrets through `NEXT_PUBLIC_` variables.

## Frontend Checklist

Before going live:

```text
Guest checkout captures uploadToken from the create-order response
Guest upload sends orderId + customerEmail + uploadToken
Logged-in upload sends Authorization: Bearer ***
Order is created first, then photos are uploaded per order item
403 (bad/expired token) and 409 (item full) are handled as distinct states
No image keys are sent back to the backend on order update
No image URL is cached beyond 5 minutes; URLs refresh on demand
Order tracking uses order number + email, never userId
Order lists use /api/orders/my, never /api/orders?userId=
Payment state is read from the order, not from return-URL query params
CORS_ORIGINS on Fly lists only the production frontend domains
```

## Backend Production Checklist

```text
R2 bucket is private; raw object URLs do not resolve
Presigned URL expiry is 300s (FileService / OrderMapper)
CORS_ORIGINS only includes Vercel/custom production domains
ToyyibPay callback points to Fly backend; return URL points to Vercel
TOYYIBPAY_VERIFY_CALLBACK=true
SETUP_ADMIN_ENABLED=false
Swagger (/swagger-ui/**, /v3/api-docs/**) is publicly reachable in prod by design — confirm that is acceptable
GET /api/orders?userId=... is still public (known open gap, tracked separately)
```
