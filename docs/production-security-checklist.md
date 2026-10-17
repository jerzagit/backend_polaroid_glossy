# Production Security Checklist

> **Status: updated 2026-10-18.** This version supersedes every earlier claim in this
> file (first written 2026-06-27 against the pre-R2 design). Each statement below was
> re-verified against `SecurityConfig`, `FileController`, `FileService`, `OrderController`,
> `OrderService`, `PaymentService`, `WebhookController`, `storage/*` and
> `application-prod.yml`. Statements about the old design are marked **Historical**.
> One real gap is still open — see [Outstanding Fix](#outstanding-fix) at the end.

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
  STORAGE_PROVIDER='r2' \
  STORAGE_R2_ACCOUNT_ID='...' \
  STORAGE_R2_API_TOKEN='...' \
  STORAGE_R2_BUCKET='bucketpolaroidglossy2026' \
  STORAGE_R2_ACCESS_KEY='...' \
  STORAGE_R2_SECRET_KEY='...' \
  STORAGE_R2_REGION='auto' \
  STORAGE_R2_PATH_STYLE='true' \
  TOYYIBPAY_SECRET_KEY='...' \
  TOYYIBPAY_CATEGORY_CODE='...' \
  TOYYIBPAY_RETURN_URL='https://your-vercel-domain/payment-status' \
  TOYYIBPAY_CALLBACK_URL='https://your-fly-app.fly.dev/api/webhooks/toyyibpay' \
  TOYYIBPAY_VERIFY_CALLBACK='true' \
  CORS_ORIGINS='https://your-vercel-domain,https://your-custom-domain' \
  SETUP_ADMIN_ENABLED='false'
```

Notes:

- `STORAGE_R2_S3_ENDPOINT` is optional and may be left unset; it is only needed when
  pointing at a non-default S3 endpoint. `STORAGE_R2_ACCESS_KEY` and
  `STORAGE_R2_SECRET_KEY` **are** required — `R2StorageService` cannot presign URLs
  without them (it throws `R2 signed URL credentials are not configured`).
- `TOYYIBPAY_VERIFY_CALLBACK` defaults to `true`, but set it explicitly in prod.
- Only set `SETUP_ADMIN_ENABLED=true` temporarily while creating the first admin, and
  set a strong `SETUP_ADMIN_SECRET` when you do.
- **Historical:** earlier versions of this checklist told you to set `SUPABASE_URL`,
  `SUPABASE_KEY` and `SUPABASE_SIGNED_URL_EXPIRATION`. Those properties still exist in
  `application.yml`/`application-uat.yml` as legacy pass-through values, but no storage
  code path uses Supabase any more (`storage/` contains `LocalStorageService`,
  `R2StorageService`, `S3StorageService`). Do not add them to new deployments.

## Vercel Frontend Environment

Set this in Vercel Production environment variables:

```bash
NEXT_PUBLIC_API_URL=https://your-fly-app.fly.dev/api
```

(The frontend repo also reads `NEXT_PUBLIC_BACKEND_API_BASE` — keep whichever name the
current frontend build actually uses, but do not introduce both.)

Do not expose backend-only secrets in Vercel variables prefixed with `NEXT_PUBLIC_`.

## Object Storage (Cloudflare R2)

Production uses Cloudflare R2 through its S3-compatible API, selected by
`app.storage.provider: ${STORAGE_PROVIDER:r2}` in `application-prod.yml`. Dev defaults to
the local filesystem (`app.storage.provider: local`); a plain AWS S3 strategy
(`s3`) also exists. **Historical:** the Supabase Storage bucket (`polaroid-glossy`) is
legacy and is no longer written to by the backend.

The R2 bucket must be private. The backend stores image keys like:

```text
orders/{orderNumber}/{orderItemId}/{uuid}.jpg
orders/{orderNumber}/{orderItemId}/upload-{photoId}.jpg
payment-proofs/{orderNumber}/{uuid}.jpg
```

(The earlier `orders/{orderId}/original/{uuid}.jpg` layout is **Historical** — the key is
derived from the order *number* and the order *item id*, and there is no `original/`
segment.)

Nothing in the app serves public object URLs. Viewing is always through short-lived
presigned URLs:

- `FileService.IMAGE_URL_EXPIRATION_SECONDS = 300` — every `getSignedUrl(...)` call in
  `FileService` (user uploads, payment proofs, asset uploads) and `OrderMapper`
  (`IMAGE_URL_EXPIRATION_SECONDS = 300`) uses 300 seconds.
- `R2StorageService.DEFAULT_SIGNED_URL_EXPIRATION_SECONDS = 300` as well.
- **Historical:** `SUPABASE_SIGNED_URL_EXPIRATION=3600` no longer governs anything.

Practical consequence: any image URL the frontend renders for more than five minutes
must be re-fetched (re-upload response, order reload, or a fresh list call), not cached
in the database or in local storage.

## Request Authorization Matrix

From `SecurityConfig` plus method-level `@PreAuthorize`:

| Endpoint | Rule |
|---|---|
| `POST /api/files/upload` | **Public** (`permitAll`), but token-gated: a guest must send `orderId` + `customerEmail` + `uploadToken`; a logged-in user is authorized by JWT and must own the order or be staff. Anonymous uploads are rate-limited to 10 per 10 minutes per client IP + order. |
| `POST /api/files/payment-proof` | Authenticated (`isAuthenticated()`); order must belong to the caller and be a bank-transfer order awaiting proof. |
| `DELETE /api/files?key=...` | `ADMIN`, `MARKETING`, `PACKER`; http(s)/`://` keys rejected. |
| `GET /api/files/order/{orderId}` | `ADMIN` only. |
| `GET /api/files/order/{orderId}/download` | `ADMIN`, `MARKETING`, `PACKER`. |
| `GET /api/files/order/{orderId}/items/{itemId}/...` (strip exports) | `ADMIN`, `MARKETING`, `PACKER` (controller-level `@PreAuthorize`). |
| `POST /api/orders` | Public — creates a guest draft. Guest response includes a one-time `uploadToken` (hash + expiry stored on the order, default 168h). |
| `GET /api/orders/{orderNumber}` | Public, but only returns data for the owner's JWT, a matching `?email=` query value, or an authenticated staff account. Otherwise 403. |
| `GET /api/orders?userId=<uuid>` | **Public and unchecked — see Outstanding Fix.** |
| `GET /api/orders/my` | Authenticated (falls through to `anyRequest().authenticated()`). |
| `POST /api/orders/{orderNumber}/pay` | Authenticated. |
| `POST /api/webhooks/toyyibpay` | Public, verified in `PaymentService.verifyCallback` (refno present, bill code matches the order, amount matches the order total in cents, callback hash matches); rejected callbacks return **403**. |
| `/swagger-ui/**`, `/v3/api-docs/**` | **Public in every profile, including prod.** |

### Upload validation (public endpoint, so this matters)

`FileService.validateAndProcessImage` rejects, in order: empty file, `> 25 MB`,
magic-byte format outside JPEG/PNG/WEBP, undecodable images, decoded size `> 100 MB`
(width × height × 4), and dimensions `> 10000 px` on either side. Accepted images are
**re-encoded** server-side, which drops EXIF/metadata and neutralises polyglot files.
Upload capacity per order item and order state (`PENDING`/`PROCESSING`) are also enforced.

## Swagger

`/swagger-ui/**` and `/v3/api-docs/**` are `permitAll()` in `SecurityConfig`, which is not
profile-scoped, so the API docs are publicly reachable in production. This is a deliberate
current state, not a bug in the config — but confirm it is intended for each deployment.
If the API surface must not be discoverable, that requires a code change (profile-scoped
`springdoc` disabling); it is **not** implemented today.

## Required Production Checks

- Confirm `/api/files/upload` is reachable anonymously **only** with a valid
  `orderId` + `customerEmail` + unexpired `uploadToken`; a wrong email or token must 403,
  and a wrong `orderId` must not leak order data.
- Confirm anonymous upload rate limiting is effective behind the Fly proxy (it keys on
  `X-Forwarded-For`, then `X-Real-IP`, then `remoteAddr`).
- Confirm the R2 bucket is private: a raw object URL (no presign) must not work.
- Confirm presigned URLs expire in ~300 seconds and that the frontend refreshes them.
- Confirm `STORAGE_R2_ACCESS_KEY` / `STORAGE_R2_SECRET_KEY` are set, otherwise presigning
  fails at runtime.
- Confirm `/api/auth/setup-admin` returns 404 when `SETUP_ADMIN_ENABLED=false`.
- Confirm ToyyibPay callbacks are rejected (403) when the hash, bill code, amount, or
  order reference is wrong, and that `TOYYIBPAY_VERIFY_CALLBACK=true`.
- Confirm `CORS_ORIGINS` (comma-separated) contains only the Vercel/custom production
  domains.
- Confirm customer file listing/download is staff-only (`GET /api/files/order/{orderId}`
  is ADMIN; the zip download is ADMIN/MARKETING/PACKER).
- Confirm `GET /api/orders?userId=...` is still open, and track it as a known risk until
  the Outstanding Fix lands.

## Outstanding Fix

**`GET /api/orders?userId=<uuid>` is public and returns that user's orders.**

- `SecurityConfig` permits all `GET /api/orders`.
- `OrderController.getOrders` passes `userId` straight to
  `OrderService.getOrdersByUserId`, which performs no ownership or role check.
- The UUID is discoverable: `GET /api/reviews` is public and `ReviewResponse` includes
  `userId`, so an attacker can harvest a customer's UUID from public reviews and then
  enumerate every order belonging to them (order numbers, items, totals, shipping
  address fields exposed by `OrderResponse`).

This is **not fixed**. Intended fix: drop the `userId` branch from
`GET /api/orders` (clients should use `GET /api/orders/my`), or require authentication
and assert `userId == authenticated user` unless the caller is ADMIN. Until then, treat
customer UUIDs as non-secret and do not build further features on this parameter.
