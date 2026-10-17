# Backend Architecture — Polaroid Glossy

Status: verified against the source at commit `b73ca40` (18 Oct 2026). Companion
documents: [`docs/API.md`](docs/API.md) for the endpoint contract,
[`docs/production-workflow.md`](docs/production-workflow.md) for release process. This
replaces the July 2026 version, which described the pre-catalog, Supabase-era design.

## 1. What the system does

A Malaysian photo-print shop runs on one Spring Boot service that serves four audiences:

| Audience | Surface |
|---|---|
| Storefront customers | Public catalog, guest/authenticated checkout, photo uploads, ToyyibPay, order tracking |
| Affiliates | Same customer API plus an affiliate code that is stored on the order |
| Sellers / operators | JSON admin API (`/api/admin/**`) and a Thymeleaf console (`/admin/**`) |
| Print operators | Strip design exports (per item, design or slot) as downloads |

The Next.js frontend lives in `frontend/` and deploys to Vercel separately; it talks to
this service over JSON.

## 2. Runtime and stack

| Layer | Choice |
|---|---|
| Framework | Spring Boot 3.4.1, Java 17 (`release=17`) |
| Web | Spring MVC, REST controllers, Thymeleaf for `/admin` |
| Persistence | Spring Data JPA / Hibernate; PostgreSQL (Supabase-hosted Postgres 17 in prod) |
| Migrations | Flyway, 30 SQL files `V1`…`V29` (including `V19_1`) |
| Security | Spring Security 6: JWT filter, form login for the console, `@PreAuthorize` |
| Tokens | jjwt 0.12.5, HS256, 24 h access / 7 day refresh |
| Storage | `StorageService` strategies: local filesystem (dev), Cloudflare R2 over the AWS S3 API (prod), plain S3 (`s3`) |
| Payments | ToyyibPay bill API + verified callback; bank transfer with proof upload |
| Images | ImageIO + TwelveMonkeys WebP reader, re-encode to PNG/JPEG server-side |
| API docs | springdoc-openapi 2.5.0, `/v3/api-docs`, `/swagger-ui/index.html` |
| Build | Maven 3.9.6 (`mvn`; the checked-in `mvnw.cmd` is broken on Windows) |

## 3. Module map

```
controller/ (17)            service/ (22)                 repository/ (15)
  AuthController              AuthService                    UserRepository
  OrderController             OrderService, Fulfillment      OrderRepository, OrderItemRepository
  AddressController           PaymentService, PaymentCleanup OrderStatusHistoryRepository
  ProductController           ProductService, CatalogPricing ProductRepository
  ProductImageController      ProductImageService            ProductVariantRepository
  PrintSizeController         PrintSizeService               ProductPackageRepository
  ReviewController            ReviewService                  ProductMetaRepository
  TestimonialController       TestimonialService             ProductAliasRepository
  FileController              FileService                    PrintSizeRepository
  StripExportController       StripCheckoutService           PrintSizePricingTierRepository
  CatalogAdminController      StripDesigns, StripExportService AddressRepository
  AdminController             CatalogAdminService            ReviewRepository
  SystemController            StatsService                   TestimonialRepository
  ToyyibpayController         SystemService
  WebhookController           DraftCleanupService
  HealthController            EmailService, UserService
  WebController (Thymeleaf)   AddressService

config/    SecurityConfig, CorsConfig, GlobalExceptionHandler, JpaAuditingConfig,
           RestTemplateConfig, SeedDataConfig, DateTimeConfig
security/  JwtAuthenticationFilter, JwtTokenProvider, CustomUserDetailsService
storage/   StorageService + LocalStorageService / R2StorageService / S3StorageService
model/     Order, OrderItem, OrderStatusHistory, Product, ProductVariant, ProductPackage,
           ProductMeta, ProductAlias, PrintSize, PrintSizePricingTier, User, Address,
           Review, Testimonial, StripDesignState, base/Auditable, enums/*
dto/       request/ + request/admin/, response/, mapper/
```

## 4. Request lifecycle

1. **Filter chain** — `JwtAuthenticationFilter` reads `Authorization: Bearer`, validates the
   signature and expiry, loads the user and populates the `SecurityContext`. It never
   rejects; authorization happens in the next step.
2. **Authorization** — `SecurityConfig` applies URL rules in declaration order (public
   allow-list, then `/api/admin/**` role groups, then `anyRequest().authenticated()`);
   `@PreAuthorize` on handlers/classes narrows the allowed roles further. Both must pass.
3. **Controller** — validates the DTO (`@Valid`), resolves the caller from `Authentication`,
   delegates to a service. Controllers hold no business rules beyond request shape and
   service selection (for example `createOrder` routes strip payloads to
   `StripCheckoutService`).
4. **Service** — transactional boundaries, authorization on the *object* (owner, staff),
   pricing, state transitions, storage calls, email.
5. **Repository / DB** — Spring Data JPA. `orders.version` gives optimistic locking;
   checkout and payment paths additionally take pessimistic row locks
   (`orderRepository.lockById`).
6. **Errors** — domain exceptions are translated by `GlobalExceptionHandler` into
   `{success:false, message, …}` with 400/401/403/404/409/413/500 (see `docs/API.md`).
7. **Serialisation** — DTOs only; entities never leave the service layer. Dates are
   `LocalDateTime` in Malaysia local time.

The Thymeleaf console uses the same filter chain but authenticates through form login and a
`JSESSIONID` session (`SessionCreationPolicy.IF_REQUIRED`); the JSON API is effectively
stateless in practice because clients send the bearer token on every call.

## 5. Data model

Flyway owns the schema; Hibernate runs with `ddl-auto: validate` in prod and uat, `update`
in dev with Flyway disabled.

| Table | Notes |
|---|---|
| `users` | Email (unique), BCrypt password, role, phone, avatar, affiliate code, active flag |
| `addresses` | Owned by user, one `is_default` row enforced in service code |
| `products` | Catalog root: `code` (slug, unique), name, dimensions, base price, tag, active |
| `product_variants` | `style` (`FULL`/`BORDER`), label, sort order, active, `cover_image_url` (V29, nullable) |
| `product_packages` | Package quantity and whole-package `price`, sort order, active; belongs to a variant |
| `product_meta` | Marketing text, ordered `images` JSON array, specs, features, accent colour, TikTok videos |
| `product_aliases` | Legacy URL ids (`ic-border`, `2r-no-border`, …) mapped to product + style filter |
| `print_sizes`, `print_size_pricing_tiers` | Legacy per-print catalogue: still used by old orders, admin settings and the `sizeId` checkout path |
| `orders` | Order header: customer snapshot, totals, status, payment status/method, upload mode, tracking, upload-token hash + expiry, draft expiry, `version` |
| `order_items` | Line snapshot: size, variant/package ids and labels, package count, unit/total price, `s3_keys`, `images`, `custom_texts`, `strip_design` JSON (V28), expected/uploaded counts |
| `order_status_history` | Append-only audit of status changes with message and timestamp |
| `strip_checkout_requests` | Idempotency ledger for strip checkouts: request hash, order id, encrypted upload token |
| `reviews`, `testimonials` | Marketing content; reviews carry optional `size_id`/`order_id` |

Design rules that matter:

- **Snapshots over joins.** `order_items` copies the catalogue selection and price at
  checkout (`V25`), so editing a package price never rewrites an existing order.
- **Catalog is additive.** V23–V27 introduced products/variants/packages and retired the
  duplicate print-size rows; V28 added strip design state and checkout idempotency; V29
  added per-style covers. Nothing dropped the legacy tables.
- **JSON columns** (`strip_design`, `images`, `s3_keys`, `custom_texts`) keep the strip
  contract flexible without new tables.

## 6. Catalog and pricing

- Public reads: `GET /api/products` and `GET /api/products/{id}` where `{id}` may be a
  numeric id, a product `code`, or a legacy alias (aliases filter the response to their
  style). The response carries `variants[]` (style, label, `coverImageUrl`, `packages[]`)
  and the legacy `pricingTiers` for older clients.
- Catalogue administration is `ADMIN` for writes and `ADMIN`/`MARKETING` for reads.
  Products, variants and packages are created/patch individually; packages are priced as a
  whole-package total (`quantity` is immutable after creation).
- Product galleries hold at most 12 distinct HTTPS URLs in `product_meta.images`; the first
  image is the product-level `image`. Uploaded files are re-encoded to PNG and served from
  `GET /api/products/{id}/images/{name}`, which only serves gallery members. A style cover
  must point at one of those gallery URLs and is cleared in the same transaction when that
  image is removed.
- **Pricing is server-side.** `CatalogPricingService` resolves the active
  product/variant/package, computes `packagePrice × packageCount`, and
  `assertOrderPricingCurrent` re-checks an order before a bill is created. If a client
  supplies `expectedTotal` and it no longer matches, checkout fails with
  `409 PRICE_CHANGED` + `currentTotal`; the client reconfirms through
  `POST /api/orders/{orderNumber}/confirm-price`, which re-syncs item prices
  (`resyncOrderItemPrices`) and recomputes shipping.
- The legacy `sizeId` path still prices from `print_sizes.price` for old clients and old
  products; new work should send `productId` + `variantId` + `packageId`.
- Shipping: RM 7.00 Peninsular Malaysia, RM 11.00 Sabah/Sarawak/Labuan; country is fixed to
  Malaysia.

## 7. Orders and checkout

`POST /api/orders` is public. Behaviour by caller:

| Caller | Status on create | Upload token | Idempotency |
|---|---|---|---|
| Guest | `DRAFT`, `expiresAt = now + 24 h` | Yes, returned once, stored hashed, valid 168 h | Not deduplicated (key ignored) |
| Authenticated | `PENDING` | No | Deduplicated per user by `checkoutKey` |
| Strip design (guest or user) | as above | as above | Deduplicated by `strip_checkout_requests`, replay returns the same order and token |

- `uploadMode: later` requires authentication (403 for guests).
- Items: 1–20 per order, each with `packages` count and `quantity`; `expectedImageCount`
  drives upload capacity. `expectedTotal` is optional but recommended.
- Order numbers are `PG` + the last 8 digits of epoch millis + 4 hex characters (not
  sequential, not guessable from a timestamp alone, but also not a secret).
- Status history is written for every transition. Drafts expire through
  `DraftCleanupService`; unpaid orders expire through `PaymentCleanupService`.
- Cancellation (`DELETE /api/orders?orderId=`) accepts an order id or order number, requires
  the owner or staff, and currently does not guard on status — a paid order can be
  cancelled without a refund step. Treat it as staff tooling.
- `GET /api/orders/my` returns the caller's orders with pagination; `GET /api/orders/{n}`
  requires owner JWT, matching `?email=`, or staff.

## 8. Payments

**ToyyibPay.** `PaymentService.billOrder` recomputes the amount from the stored order
total, asserts pricing is still current, then calls `createBill` and returns
`{billCode, paymentUrl, orderNumber}`. Both
`POST /api/orders/{orderNumber}/pay` (JWT) and `POST /api/toyyibpay/create-bill` (public,
optional `customerEmail` that must match the order) reach it.

The callback `POST /api/webhooks/toyyibpay` is public but verified when
`TOYYIBPAY_VERIFY_CALLBACK=true` (prod default): `refno` present, `billcode` equal to the
stored `toyyibpay_ref`, `amount` equal to the order total in cents, and the callback hash
matching. Failures raise `SecurityException` → **403**, and no state changes. Accepted
callbacks map gateway status to `PaymentStatus` and update the order.

`GET /api/orders/payment-return` resolves the gateway redirect into
`{orderNumber, paymentStatus, orderStatus, gatewayStatus}` so the frontend can render a
result page without trusting query strings.

**Bank transfer.** Orders created with `paymentMethod: bank_transfer` accept a proof image
via `POST /api/files/payment-proof` (owner only, order must be pending/draft and unpaid).
Staff verify it with `POST /api/admin/orders/{orderNumber}/verify-payment`.

**Mock payments.** `app.mock-payments.enabled` (dev only) exposes
`POST /api/orders/{orderNumber}/mock-pay`, which marks the order paid; it 404s in prod.

**Jobs.** `PaymentCleanupService` runs hourly: expire unpaid orders past
`PAYMENT_EXPIRATION_HOURS` (24) and email a reminder at `PAYMENT_REMINDER_HOURS` (14) when
photos or payment are missing. `EmailService` renders Thymeleaf templates
(`email/order-confirmation`, `email/payment-confirmation`, `email/payment-reminder`) and
swallows SMTP failures: confirmations are fire-and-forget void methods, the two reminder
methods return a boolean so the job can leave the "reminder sent" timestamp unset.

## 9. Files and storage

- `StorageService` is the seam: `LocalStorageService` (`app.storage.provider=local`,
  `.dev-storage/`), `R2StorageService` (`r2`, Cloudflare S3-compatible endpoint, path-style
  addressing), `S3StorageService` (`s3`). Production uses R2 with a private bucket.
- Key layout:
  - customer photos `orders/{orderNumber}/{orderItemId}/{uuid}.jpg|.png` or `upload-{photoId}.{ext}`
  - strip assets `orders/{orderId}/assets/{assetId}/original.{ext}`
  - payment proofs `payment-proofs/{orderNumber}/{uuid}.jpg|.png`
  - catalog images `catalog/{productId}/{uuid}.png`
- Reads never use public object URLs: `getSignedUrl` presigns for 300 seconds (FileService,
  OrderMapper and R2 defaults all use 300).
- Validation: ≤ 25 MB, magic-byte JPEG/PNG/WebP, re-encoded server-side (strips EXIF),
  ≤ 10 000 px per side, ≤ 100 MB decoded. Capacity per item and order state
  (`PENDING`/`PROCESSING`) are enforced.
- Guest uploads need `orderId` + `customerEmail` + unexpired `uploadToken`, and are rate
  limited to 10 attempts per 10 minutes per client IP + order number.
- `DELETE /api/files?key=` rejects absolute URLs and is limited to
  `ADMIN`/`MARKETING`/`PACKER`.

## 10. Strip designs

Strip products (`strip-3`, `strip-4`) are physical strips; the backend never composites
images. `GET /api/orders/capabilities` publishes the contract: schema version 1, max 100
designs, max 400 assets, idempotency key required.

- Checkout sends `stripDesign { schemaVersion, assets[{clientAssetId}], designs[{clientDesignId, copies, slots[{position, clientAssetId}]}] }`.
  `StripDesigns.reserve` enforces schema version 1, 1–100 designs and 1–400 assets, exactly
  `slotCount` numbered slots per design, unique asset references, every declared asset used,
  and design copies summing to the purchased quantity; it then reserves each asset
  (`assetId` returned through the order).
- `StripCheckoutService` requires a UUID `Idempotency-Key`/`checkoutKey`, records the
  request hash in `strip_checkout_requests`, and replays the original order (including the
  upload token, encrypted with a key derived from `JWT_SECRET`) for duplicate requests.
- Uploads use `POST /api/files/upload` with `assetId`; the service tracks
  `uploadedAssetCount` / `expectedAssetCount` and reports `fulfillmentReady`.
- Seller exports: `GET /api/files/order/{orderId}/items/{itemId}/download` (whole item ZIP),
  `.../designs/{designId}/download`, `.../designs/{designId}/slots/{position}/download` —
  all `ADMIN`/`MARKETING`/`PACKER`.

## 11. Security model

| Concern | Implementation |
|---|---|
| Customer auth | JWT bearer, HS256, 24 h access / 7 day refresh, BCrypt passwords |
| Console auth | Form login (`/admin/login`, email + password), session cookie, `/admin/logout` |
| Roles | `CUSTOMER`, `AFFILIATE`, `PACKER`, `MARKETING`, `ADMIN` |
| Admin API | `/api/admin/system/**` → `ADMIN`; other `/api/admin/**` → `ADMIN`/`MARKETING`/`PACKER`; user + print-size writes → `ADMIN` |
| Public | catalog reads, auth entry points, order create/lookup, uploads (token-gated for guests), webhooks, health, Swagger |
| CORS | `CORS_ORIGINS` (comma separated), all methods, `Authorization` + `Content-Disposition` exposed |
| CSRF | Disabled (token-based API; the console is cookie-based and relies on SameSite defaults) |
| Object-level checks | ownership or staff enforced in services for order reads/writes, uploads, proofs |

Open security gaps (deliberately documented, not fixed in this documentation pass):

1. `GET /api/orders?userId=<uuid>` is public and unchecked; reviewer UUIDs are exposed by
   `GET /api/reviews`, so this is exploitable end-to-end.
2. `POST /api/reviews` takes `userId`/`orderId` from the body and trusts them.
3. `POST /api/auth/google` issues a token for a body-supplied email.
4. Swagger UI and `/v3/api-docs` are public in production.
5. `DELETE /api/orders` does not restrict which statuses may be cancelled.

## 12. Background jobs

| Class | Cadence | Work |
|---|---|---|
| `DraftCleanupService.cleanupExpiredDrafts` | 10 min | `DRAFT` past `expiresAt` → `EXPIRED` + history |
| `PaymentCleanupService.cleanupExpiredPayments` | 1 h | Unpaid orders past `PAYMENT_EXPIRATION_HOURS` → expire |
| `PaymentCleanupService.remindMissingPhotos` | 1 h | Email payment/upload reminders past `PAYMENT_REMINDER_HOURS` |

Jobs run inside the app process, so with two Fly machines each job runs twice per interval;
the operations are idempotent by state check but not coordinated by a lock.

## 13. Configuration and profiles

`application.yml` holds shared config and activates `dev` by default.

| Setting | dev | prod |
|---|---|---|
| Datasource | `jdbc:postgresql://localhost:5433/polaroid`, `postgres`/`password` | `DATABASE_URL` (required) |
| Flyway | disabled | enabled, `baseline-on-migrate=true`, `baseline-version=2` |
| Hibernate | `ddl-auto: update` | `ddl-auto: validate` |
| Storage | local `.dev-storage` | R2 (`STORAGE_PROVIDER`) |
| Payments | mock enabled | ToyyibPay, `TOYYIBPAY_VERIFY_CALLBACK=true` |
| Mail | `localhost:1025`, no auth | `SPRING_MAIL_*`, STARTTLS |
| Setup admin | enabled (`admin-secret-2024`) | disabled |
| Logging | `com.polaroid: DEBUG` | `WARN` |
| Public URL | – | `PUBLIC_API_URL` (image URLs are built from it), `FRONTEND_URL` |

Prod fails fast on missing `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`CORS_ORIGINS`, the ToyyibPay keys and the R2 account/token; S3 access/secret keys are
required for presigning. `SUPABASE_*` properties remain in `application.yml`,
`application-uat.yml` and `fly.toml` but no storage code path reads them any more.

`SeedDataConfig` seeds legacy print sizes, testimonials and sample reviews at startup when
those tables are empty (all profiles).

## 14. Testing

`mvn test` runs 12 classes / 62 tests against in-memory H2 in PostgreSQL mode:

- `FileServiceSecurityTest` (15), `FileServiceFulfillmentTest` (4) — authorization, upload
  validation, fulfillment readiness
- `CatalogPricingServiceTest` (12) — package pricing, price-conflict detection
- `VariantCoverImageTest` (8) — cover set/clear, gallery validation, cleanup
- `ProductImageServiceTest` (6), `ProductCatalogMappingTest` (5)
- `OrderServiceFulfillmentTest` (2), `OrderServicePackagePricingTest` (4)
- `LoginExceptionHandlingTest` (3), `ProductImageAuthorizationTest` (1),
  `CatalogSchemaMappingTest` (1), `ProductMetaMappingTest` (1)

There is no end-to-end test against Postgres and no test for `GET /api/orders?userId=`, the
gap listed above. Schema compatibility is covered indirectly by Hibernate validation in
prod and by `CatalogSchemaMappingTest`/`ProductMetaMappingTest`.

## 15. Deployment and operations

- **Runtime:** Fly.io app `polaroid-glossy-backend`, region `sin`, two `shared-cpu-1x`
  machines with 512 MB, `min_machines_running = 2`, health check `GET /api/health` every
  30 s (180 s grace).
- **Images:** `Dockerfile` builds with Maven 3.9 + Temurin 17, ships a JRE 17 image, runs
  `java -jar app.jar` with `SPRING_PROFILES_ACTIVE=prod`.
- **CI:** `.github/workflows/ci.yml` compiles and tests on push/PR to `main`.
  `fly-deploy.yml` triggers on CI completion (`workflow_run`) and deploys only when CI
  succeeded for a push to `main`, checking out the tested SHA.
- **Manual deploy:** `scripts/deploy-prod.sh` (preflight, `flyctl deploy --remote-only`,
  health poll) for when Actions is unavailable.
- **Migrations** run at boot; `baseline-on-migrate` + `baseline-version=2` mean an existing
  pre-Fly deployment counts as applied through V2.
- **Logs/metrics:** application logs to stdout (`flyctl logs`); admin system endpoints expose
  storage, database, server and payment-cost summaries.

## 16. Known gaps and next steps

| Area | Gap |
|---|---|
| Authorization | Public `GET /api/orders?userId=`; review author spoofing; public Google login |
| Order lifecycle | Cancellation has no status guard; no refund workflow |
| Idempotency | Guest (non-strip) checkout is not deduplicated |
| Jobs | Scheduled work is not leader-elected across machines |
| Docs surface | Swagger public in prod |
| Tests | No Postgres/end-to-end suite; the order-enumeration gap is untested |
| Legacy | `print_sizes`/`pricingTiers` still in the read path; `SUPABASE_*` config left over |
