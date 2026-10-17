# API Reference

REST API for the Polaroid Glossy storefront, seller dashboard and admin UI.

**Verified against the source at `b73ca40` (18 Oct 2026).** The running service
generates its own schema from the compiled code at `/v3/api-docs`, with a
browsable UI at `/swagger-ui/index.html`. If this file and that schema disagree,
the schema is right and this file needs a fix.

## Base URLs

| Environment | API base | Notes |
|---|---|---|
| Local (`dev` profile, default) | `http://localhost:8080/api` | Postgres on `localhost:5433`, Flyway disabled, mock storage in `.dev-storage`, mock payments enabled |
| Production (`prod` profile) | `https://polaroid-glossy-backend.fly.dev/api` | Fly.io, 2 machines, region `sin`, Flyway migrations on boot |
| Admin UI (Thymeleaf) | `https://polaroid-glossy-backend.fly.dev/admin` | Server-rendered; not part of the JSON API |

There is no version prefix (`/api/v1` does not exist). Changes are additive; see
[Keeping this document honest](#keeping-this-document-honest).

## Conventions

- JSON request/response bodies unless the endpoint is `multipart/form-data` (uploads) or a file download.
- Authenticated calls send `Authorization: Bearer <token>`.
- Timestamps are ISO-8601 local date-times without an offset (`2026-10-18T01:23:45`), serialised by `DateTimeConfig`.
- Money is `BigDecimal` in MYR with 2 decimal places. Order totals are JSON numbers; catalog package prices are strings (`"12.00"`).
- Response envelopes are inconsistent by design of history: most writes answer `{"success": true, ...}`, while catalog/product reads return the DTO (or array) bare. Follow the examples below per endpoint.
- Pagination uses `page` (0-based), `size`, `sortBy`, `sortDir`; `/api/orders/my` and `/api/admin/orders` also return `totalPages` / `totalElements`.
- Idempotent checkout: send `Idempotency-Key: <key>` (or `checkoutKey` in the body — if both
  are present they must match). Replay behaviour differs by path: authenticated orders are
  deduplicated per user, and strip-design checkouts are deduplicated for guests and users
  alike through `strip_checkout_requests` (reusing a key with different contents returns
  `409`). Plain **guest** checkouts are not deduplicated — the key is ignored for anonymous
  callers, so a retried guest request creates a second draft.
- Shipping is computed server-side from `customerState`: RM 7.00 for Peninsular Malaysia,
  RM 11.00 for Sabah, Sarawak and Labuan. `customerCountry` is forced to `Malaysia`.
- Upload limits: 25 MB per request (Spring multipart), JPEG/PNG/WebP identified by magic bytes, max 10 000 px per side, max 100 MB decoded per image, order item counts 1–20, product gallery max 12 images.

## Authentication and roles

### Customer / staff tokens

1. `POST /api/auth/register` or `POST /api/auth/login` returns `AuthResponse`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "customer@example.com",
  "name": "Aisyah",
  "role": "CUSTOMER",
  "expiresIn": 86400
}
```

2. Send `Authorization: Bearer <token>` on every authenticated call.
3. `POST /api/auth/refresh` exchanges a refresh token for a new pair. In the current
   security configuration this path is **not** in the public allow-list, so it must be
   called while the access token is still valid.
4. `GET /api/auth/me` returns the current `UserResponse`; `GET /api/auth/profile`
   returns the same user plus order/address counters.

Roles: `CUSTOMER`, `AFFILIATE`, `PACKER`, `MARKETING`, `ADMIN`. Affiliates are ordinary
customers with an `affiliateCode`; the admin roles unlock `/api/admin/**` per the tables below.

### Admin UI session

`/admin/**` uses Spring form login (`/admin/login`, fields `username` = email and
`password`), backed by a `JSESSIONID` session. `POST /admin/logout` ends it. The same
`ADMIN`/`MARKETING`/`PACKER` roles gate the pages.

### Public endpoints

Everything in the tables marked **Public** is reachable without a token:

`/api/health`, `/api/products/**` (GET), `/api/print-sizes`, `/api/testimonials`,
`GET /api/reviews`, `/api/auth/{login,register,google,setup-admin}`, `/api/orders`
(GET and POST), `/api/orders/{single-segment}` (GET), `/api/webhooks/**`,
`POST /api/toyyibpay/create-bill`, `POST /api/files/upload`, `/admin/login`,
`/admin/logout`, `/swagger-ui/**`, `/v3/api-docs/**`.

`POST /api/auth/setup-admin` is public at the URL level but returns **404** unless
`SETUP_ADMIN_ENABLED=true`, and **401** when enabled but the `secret` query parameter
does not equal `SETUP_ADMIN_SECRET`. Production runs it disabled.

### Known authorization gaps

These are current behaviour, not aspirations; they are documented so clients do not
rely on them and so the fix is easy to verify:

1. `GET /api/orders?userId=<uuid>` is public and returns that user's orders, bypassing
   the email check on `GET /api/orders/{orderNumber}`. `GET /api/reviews` publishes the
   `userId` of every reviewer, so the UUID needed for that call is obtainable.
2. `POST /api/auth/google` trusts the `email` in the request body and issues a token for
   it. Keep that endpoint private to your auth proxy or remove it from the public surface.
3. `POST /api/reviews` is authenticated but takes `userId` and `orderId` from the body,
   so a logged-in user can file a review attributed to someone else.
4. Swagger UI and `/v3/api-docs` are public in every profile, including production.

## Errors

| Status | When | Body |
|---|---|---|
| 400 | Validation (`@Valid`), bad state, malformed ids | `{"success": false, "message": "field: message; ...", "errors": [{"field": "...", "message": "..."}]}` or `{"success": false, "message": "...", "error": "..."}` |
| 401 | Missing/expired token, bad credentials, disabled account | `{"success": false, "message": "Invalid email or password"}` |
| 403 | Authenticated but not allowed (ownership, role, rejected webhook) | `{"success": false, "message": "...", "error": "..."}` |
| 404 | Unknown order/product/id, disabled setup endpoint | `{"success": false, "message": "...", "error": "..."}` |
| 409 | Upload count exceeded (`UploadConflictException`) or price changed (`PRICE_CHANGED`) | `{"success": false, "message": "...", "code": "PRICE_CHANGED", "currentTotal": "48.00"}` |
| 413 | Multipart body above 25 MB | `{"success": false, "message": "Upload is too large. Reduce the file size and try again."}` |
| 500 | Unhandled exception | `{"success": false, "message": "Internal server error"}` |

## Endpoints

#### Catalog (public)

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/products` | Public | — |
| GET | `/api/products/{id}` | Public | `id` path |
| GET | `/api/products/{id}/images/{name}` | Public | `id` path, `name` path |

#### Orders (customer)

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/orders/capabilities` | Public | — |
| POST | `/api/orders` | Public | body `OrderRequest`, header `Idempotency-Key` |
| GET | `/api/orders` | Public | `userId` (optional), `orderNumber` (optional) |
| PUT | `/api/orders` | JWT | body `UpdateOrderRequest` |
| DELETE | `/api/orders` | JWT | `orderId` |
| GET | `/api/orders/payment-return` | Public | `order_id` (optional), `orderId` (optional), `billcode` (optional), `refno` (optional), `status` (optional), `status_id` (optional) |
| GET | `/api/orders/{orderNumber}` | Public | `orderNumber` path, `email` (optional) |
| GET | `/api/orders/my` | Public | `page`=0, `size`=20, `sortBy`=createdAt, `sortDir`=desc, `status` (optional) |
| POST | `/api/orders/{orderNumber}/pay` | JWT | `orderNumber` path |
| POST | `/api/orders/{orderNumber}/confirm-price` | JWT | `orderNumber` path |
| POST | `/api/orders/{orderNumber}/mock-pay` | JWT | `orderNumber` path, body `Map<String, String>` |

#### Addresses

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/addresses` | JWT | — |
| POST | `/api/addresses` | JWT | body `AddressRequest` |
| PUT | `/api/addresses/{id}` | JWT | `id` path, body `AddressRequest` |
| DELETE | `/api/addresses/{id}` | JWT | `id` path |
| PATCH | `/api/addresses/{id}/default` | JWT | `id` path |

#### Auth

| Method | Path | Auth | Parameters |
|---|---|---|---|
| POST | `/api/auth/register` | Public | body `RegisterRequest` |
| POST | `/api/auth/setup-admin` | Public | `secret`, body `RegisterRequest` |
| POST | `/api/auth/login` | Public | body `LoginRequest` |
| POST | `/api/auth/google` | Public | body `Map<String, String>` |
| POST | `/api/auth/refresh` | JWT | body `String` |
| GET | `/api/auth/me` | JWT | — |
| GET | `/api/auth/profile` | JWT | — |

#### Reviews & testimonials

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/reviews` | Public | `sizeId` (optional), `userId` (optional), `orderId` (optional) |
| POST | `/api/reviews` | JWT | body `Map<String, Object>` |
| GET | `/api/testimonials` | Public | — |

#### Files, uploads & exports

| Method | Path | Auth | Parameters |
|---|---|---|---|
| POST | `/api/files/upload` | Public | `file` multipart, `orderId`, `customerEmail` (optional), `uploadToken` (optional), `orderItemId` (optional), `photoId` (optional), `assetId` (optional) |
| POST | `/api/files/payment-proof` | JWT | `file` multipart, `orderId`, `reference` (optional) |
| DELETE | `/api/files` | ADMIN/MARKETING/PACKER | `key` |
| GET | `/api/files/order/{orderId}` | ADMIN | `orderId` path |
| GET | `/api/files/order/{orderId}/download` | ADMIN/MARKETING/PACKER | `orderId` path |
| GET | `/api/files/order/{orderId}/items/{itemId}/designs/{designId}/download` | ADMIN/MARKETING/PACKER | `orderId` path, `itemId` path, `designId` path |
| GET | `/api/files/order/{orderId}/items/{itemId}/download` | ADMIN/MARKETING/PACKER | `orderId` path, `itemId` path |
| GET | `/api/files/order/{orderId}/items/{itemId}/designs/{designId}/slots/{position}/download` | ADMIN/MARKETING/PACKER | `orderId` path, `itemId` path, `designId` path, `position` path |

#### Payments & webhooks

| Method | Path | Auth | Parameters |
|---|---|---|---|
| POST | `/api/toyyibpay/create-bill` | Public | body `Map<String, String>` |
| POST | `/api/webhooks/toyyibpay` | Public | `refno` (optional), `order_id` (optional), `billcode` (optional), `status` (optional), `amount` (optional), `hash` (optional) |

#### Admin: orders & stats

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/admin/stats/overview` | ADMIN/MARKETING | `status` (optional), `paymentStatus` (optional), `customerState` (optional), `fromDate` (optional), `toDate` (optional), `orderReference` (optional), `customerEmail` (optional), `customerPhone` (optional) |
| GET | `/api/admin/stats/orders-by-status` | ADMIN/MARKETING | — |
| GET | `/api/admin/stats/by-state` | ADMIN | — |
| GET | `/api/admin/stats/top-sizes` | ADMIN/MARKETING | — |
| GET | `/api/admin/orders` | ADMIN/MARKETING/PACKER | `status` (optional), `paymentStatus` (optional), `customerState` (optional), `fromDate` (optional), `toDate` (optional), `orderReference` (optional), `customerEmail` (optional), `customerPhone` (optional), `page`=0, `size`=20, `sortBy`=createdAt, `sortDir`=desc |
| GET | `/api/admin/orders/{id}` | ADMIN/MARKETING/PACKER | `id` path |
| PATCH | `/api/admin/orders/{id}/status` | ADMIN/MARKETING/PACKER | `id` path, body `OrderStatusUpdateRequest` |
| PATCH | `/api/admin/orders/{id}/tracking` | ADMIN/MARKETING/PACKER | `id` path, `trackingNumber` |
| POST | `/api/admin/orders/{id}/notes` | ADMIN/MARKETING/PACKER | `id` path, `notes` (optional) |
| PATCH | `/api/admin/orders/{id}/payment-status` | ADMIN/MARKETING | `id` path, body `UpdatePaymentStatusRequest` |
| POST | `/api/admin/orders/{orderNumber}/verify-payment` | ADMIN/MARKETING | `orderNumber` path, body `VerifyPaymentRequest` |

#### Admin: users

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/admin/users` | ADMIN | `role` (optional), `search` (optional), `page`=0, `size`=20 |
| GET | `/api/admin/users/{id}` | ADMIN | `id` path |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | `id` path, body `UserRoleUpdateRequest` |

#### Admin: print size settings

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/admin/settings/print-sizes` | ADMIN | — |
| POST | `/api/admin/settings/print-sizes` | ADMIN | body `PrintSizeRequest` |
| PATCH | `/api/admin/settings/print-sizes/{id}` | ADMIN | `id` path, body `PrintSizeRequest` |
| DELETE | `/api/admin/settings/print-sizes/{id}` | ADMIN | `id` path |

#### Admin: catalog

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/admin/catalog/products` | ADMIN/MARKETING | — |
| GET | `/api/admin/catalog/products/{id}` | ADMIN/MARKETING | `id` path |
| POST | `/api/admin/catalog/products` | ADMIN | body `CatalogProductCreateRequest` |
| PATCH | `/api/admin/catalog/products/{id}` | ADMIN | `id` path, body `CatalogProductUpdateRequest` |
| POST | `/api/admin/catalog/products/{productId}/variants` | ADMIN | `productId` path, body `CatalogVariantCreateRequest` |
| PATCH | `/api/admin/catalog/variants/{variantId}` | ADMIN | `variantId` path, body `CatalogVariantUpdateRequest` |
| PUT | `/api/admin/catalog/variants/{variantId}/cover` | ADMIN | `variantId` path, body `CatalogVariantCoverRequest` |
| POST | `/api/admin/catalog/variants/{variantId}/packages` | ADMIN | `variantId` path, body `CatalogPackageCreateRequest` |
| PATCH | `/api/admin/catalog/packages/{packageId}` | ADMIN | `packageId` path, body `CatalogPackageUpdateRequest` |
| PUT | `/api/admin/catalog/products/{id}/images` | ADMIN | `id` path, body `ProductImagesRequest` |
| POST | `/api/admin/catalog/products/{id}/images` | ADMIN | `id` path, `file` multipart |

#### Admin: system

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/admin/system/storage` | ADMIN | — |
| GET | `/api/admin/system/database` | ADMIN | — |
| GET | `/api/admin/system/payment-costs` | ADMIN | `from`, `to` |
| GET | `/api/admin/system/server` | ADMIN | — |

#### Health & legacy print sizes

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/api/health` | Public | — |
| GET | `/api/print-sizes` | Public | — |

#### Admin UI (Thymeleaf)

| Method | Path | Auth | Parameters |
|---|---|---|---|
| GET | `/admin/login` | Public | — |
| GET | `/admin` | ADMIN/MARKETING/PACKER | — |
| GET | `/admin/orders` | ADMIN/MARKETING/PACKER | `status` (optional), `paymentStatus` (optional), `search` (optional), `page`=0, `size`=20 |
| GET | `/admin/orders/{id}` | ADMIN/MARKETING/PACKER | `id` path |
| GET | `/admin/users` | ADMIN | `role` (optional), `search` (optional), `page`=0, `size`=20 |
| GET | `/admin/stats` | ADMIN/MARKETING | — |
| GET | `/admin/settings` | ADMIN | — |

## Flow examples

### 1. Guest checkout (upload later)

```http
POST /api/orders
Content-Type: application/json
Idempotency-Key: 6d1f0d9e-checkout-1

{
  "customerName": "Aisyah Rahman",
  "customerEmail": "aisyah@example.com",
  "customerPhone": "0123456789",
  "customerHouseUnitNo": "12-3",
  "customerAddressLine1": "Jalan Ampang 1",
  "customerPostcode": "50450",
  "customerCity": "Kuala Lumpur",
  "customerState": "WP Kuala Lumpur",
  "customerCountry": "Malaysia",
  "uploadMode": "later",
  "paymentMethod": "toyyibpay",
  "items": [
    {"productId": 1, "variantId": 2, "packageId": 5, "packages": 1, "quantity": 1,
     "expectedImageCount": 10}
  ]
}
```

- Guests get `status: DRAFT`, `expiresAt` (now + `app.draft.expiration-hours`, default 24 h)
  and a one-time `order.uploadToken` (valid `app.upload.token-expiration-hours`, default 168 h).
  The token is stored hashed and only returned in this create response.
- Logged-in callers get `status: PENDING` and no upload token.
- `paymentMethod` is `toyyibpay` or `bank_transfer`; `uploadMode` is `now` or `later`, and
  `later` requires a signed-in user (guests get `403 Sign in to upload photos later`).
- If `expectedTotal` is supplied and no longer matches server pricing, the call fails
  `409 PRICE_CHANGED` with `currentTotal`; re-read the order and confirm with
  `POST /api/orders/{orderNumber}/confirm-price`.

### 2. Upload files for an order

Authenticated owner:

```http
POST /api/files/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=@photo.jpg&orderId=<orderId>&orderItemId=<itemId>&photoId=<client-photo-id>
```

Guest with the upload token (no Authorization header):

```http
POST /api/files/upload
Content-Type: multipart/form-data

file=@photo.jpg&orderId=<orderId>&customerEmail=aisyah@example.com&uploadToken=<token>&orderItemId=<itemId>
```

Response includes a short-lived signed URL (`url`, 300 s) plus progress counters:

```json
{
  "success": true,
  "url": "https://...signed...",
  "fileName": "photo.jpg",
  "orderItemId": "e2f1...",
  "uploadedImageCount": 3,
  "expectedImageCount": 10,
  "remainingImageCount": 7
}
```

- Exceeding `expectedImageCount` returns `409` with `uploadedImageCount`,
  `expectedImageCount`, `remainingImageCount`.
- Anonymous uploads are rate limited to 10 per 10 minutes per client IP/order; the token
  must be unexpired and belong to that order.
- Strip-design products send `assetId` instead of `photoId`; assets are reserved during
  checkout and the response carries `uploadedAssetCount`, `expectedAssetCount`,
  `fulfillmentReady`.

### 3. Pay and confirm

```http
POST /api/orders/{orderNumber}/pay      # authenticated owner/staff; server recalculates the bill
```

- Returns `{"billCode": "...", "paymentUrl": "https://toyyibpay.com/...", "orderNumber": "..."}`.
- `POST /api/toyyibpay/create-bill` is the public equivalent and additionally accepts
  `customerEmail`, which must match the order or it fails `403`.
- ToyyibPay calls `POST /api/webhooks/toyyibpay` (`refno`, `order_id`, `billcode`,
  `status`, `amount`, `hash`). With `TOYYIBPAY_VERIFY_CALLBACK=true` (prod default) the
  bill code, reference, amount and hash must match, otherwise the callback is rejected
  with `403`. Rejected callbacks never change payment state.
- `POST /api/orders/{orderNumber}/mock-pay` exists only when
  `app.mock-payments.enabled=true` (dev profile); it returns `404` in production.
- Status polling: `GET /api/orders/{orderNumber}?email=...` or
  `GET /api/orders/payment-return?...` after the gateway redirect.

### 4. Order lookup without an account

- `GET /api/orders/{orderNumber}?email=<customer email>` — public, email must match the order.
- `GET /api/orders?orderNumber=<orderNumber>` — the same lookup through the collection route.
- Staff tokens skip the email check; order owners are identified by the JWT subject.

### 5. Per-style cover images (seller catalog)

```http
PUT /api/admin/catalog/variants/{variantId}/cover
Authorization: Bearer <ADMIN token>

{"coverImageUrl": "https://cdn.example.com/gallery/abc.png"}
```

The URL must be absolute HTTPS **and** already present in that product's gallery; removing
a gallery image through `PUT /api/admin/catalog/products/{id}/images` clears any style cover
pointing at it in the same transaction. `GET /api/products` returns the value as
`variants[].coverImageUrl` (nullable — the storefront falls back to the product image).

## Enums

| Enum | Values |
|---|---|
| `OrderStatus` | `DRAFT`, `PENDING`, `PROCESSING`, `POSTED`, `ON_DELIVERY`, `DELIVERED`, `CANCELLED`, `REFUNDED`, `EXPIRED` |
| `PaymentStatus` | `PENDING`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED` |
| `Role` | `CUSTOMER`, `AFFILIATE`, `PACKER`, `MARKETING`, `ADMIN` |
| `ProductStyle` | `FULL`, `BORDER` |

Order lifecycle: `DRAFT` (guest, awaiting payment) → `PENDING` (paid or logged-in checkout)
→ `PROCESSING` → `POSTED` → `ON_DELIVERY` → `DELIVERED`, with `CANCELLED`, `REFUNDED` and
`EXPIRED` as terminal side states. Every transition is recorded in `statusHistory`.
Drafts that never pay expire (`EXPIRED`) after `app.draft.expiration-hours`.

## Background jobs

| Job | Cadence | Behaviour |
|---|---|---|
| `DraftCleanupService.cleanupExpiredDrafts` | every 10 min | Expires `DRAFT` orders past `expiresAt` and records history |
| `PaymentCleanupService.cleanupExpiredPayments` | every hour | Expires unpaid orders past `app.payment.expiration-hours` (default 24 h) |
| `PaymentCleanupService.remindMissingPhotos` | every hour | Emails after `app.payment.reminder-hours` (default 14 h) when photos are missing |

## Keeping this document honest

- The endpoint tables above are derived from the controller annotations and
  `config/SecurityConfig.java`; the auth column is the intersection of the URL rule and
  any `@PreAuthorize` on the handler.
- After adding or changing an endpoint, update this file in the same commit and check
  `/v3/api-docs` on a running instance.
- `mvn test` covers authorization and pricing behaviour for the file, product-image,
  catalog and order paths (62 tests in 12 classes); documentation changes should not
  break it.
