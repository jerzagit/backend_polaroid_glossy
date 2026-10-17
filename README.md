# Polaroid Glossy Backend

REST API and admin console for Polaroid Glossy, a Malaysian photo-print shop. It serves
the customer storefront and seller dashboard, takes ToyyibPay payments, stores customer
photos in Cloudflare R2, and tracks each order from draft to delivery.

- API reference: [`docs/API.md`](docs/API.md)
- Architecture: [`backend_architecture.md`](backend_architecture.md)
- Local setup: [`QUICKSTART.md`](QUICKSTART.md)
- Deploying: [`docs/fly-backend-deployment.md`](docs/fly-backend-deployment.md)

## Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.4.1 (Java 17) |
| Build | Maven 3.9.6 (`mvn`; the checked-in `mvnw.cmd` is currently broken on Windows) |
| Database | PostgreSQL (Supabase-hosted in production, Postgres 17), Flyway 30 migrations |
| ORM | Spring Data JPA / Hibernate (`validate` in prod, `update` in dev) |
| Security | Spring Security, JWT access + refresh tokens (jjwt 0.12.5), form-login admin UI |
| Admin UI | Thymeleaf templates under `/admin` |
| Storage | Cloudflare R2 via AWS SDK S3 2.25.0; local filesystem in dev; S3 strategy available |
| Payments | ToyyibPay (bill creation + signed webhook callbacks) and bank transfer with proof upload |
| Images | ImageIO + TwelveMonkeys WebP reader (25 MB, JPEG/PNG/WebP, 10 000 px ceiling) |
| Docs | springdoc-openapi 2.5.0 (`/swagger-ui/index.html`, `/v3/api-docs`) |
| Tests | JUnit 5 + Spring Boot Test on H2 in PostgreSQL mode (62 tests, 12 classes) |

## What the backend owns

- **Catalog** — products, per-style variants (`FULL`, `BORDER`) and per-size packages with
  quantity pricing tiers. The legacy `print_sizes` table still backs old orders and the
  admin size settings page.
- **Checkout** — guest drafts with an upload token, authenticated checkout, idempotent
  order creation, price-change detection and reconfirmation, strip-photo design payloads.
- **Uploads** — customer photos, strip assets, payment proofs; signed URLs for reads.
- **Payments** — ToyyibPay bill creation, verified webhooks, manual payment verification,
  bank-transfer proof review, hourly expiry and reminder jobs.
- **Fulfilment** — status history, tracking numbers, seller notes, strip design exports
  (per item, per design, per slot).
- **Admin** — server-rendered console (`/admin`) plus a JSON admin API (`/api/admin/**`)
  for orders, users, stats, catalog and system info.
- **Marketing** — testimonials, reviews, TikTok video metadata.

## Quick start

```bash
# Postgres on 5432/5433, database "polaroid"
mvn spring-boot:run            # dev profile, port 8080, mock storage + mock payments
curl http://localhost:8080/api/health
```

Details, environment variables and test commands are in [`QUICKSTART.md`](QUICKSTART.md).
The dev profile writes uploads to `.dev-storage/` and enables
`POST /api/orders/{orderNumber}/mock-pay`, so checkout can be exercised without a
gateway. `SeedDataConfig` seeds the legacy print sizes, testimonials and sample reviews on
startup when those tables are empty.

## Configuration

Profiles live in `src/main/resources`: `application.yml` (shared, activates `dev`),
`application-dev.yml`, `application-uat.yml`, `application-prod.yml`. Fly sets
`SPRING_PROFILES_ACTIVE=prod`.

| Concern | dev | prod |
|---|---|---|
| Database | `jdbc:postgresql://localhost:5433/polaroid`, Flyway disabled, `ddl-auto: update` | `DATABASE_URL`, Flyway on (`baseline-on-migrate`, baseline v2), `ddl-auto: validate` |
| Storage | local `.dev-storage` | R2 (`STORAGE_PROVIDER=r2`) |
| Payments | mock payments on | ToyyibPay, callback verification on |
| Mail | MailHog-style SMTP on `localhost:1025` | `SPRING_MAIL_*` |
| Setup admin | enabled with a dev secret | disabled (`SETUP_ADMIN_ENABLED=false`) |
| CORS | localhost ports 3000-3004 | `CORS_ORIGINS` (comma separated) |

Environment variables consumed by the app (see `.env.example`):
`DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SERVER_PORT`,
`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`, `PUBLIC_API_URL`, `FRONTEND_URL`,
`STORAGE_PROVIDER`, `STORAGE_R2_ACCOUNT_ID`, `STORAGE_R2_API_TOKEN`, `STORAGE_R2_BUCKET`,
`STORAGE_R2_S3_ENDPOINT`, `STORAGE_R2_REGION`, `STORAGE_R2_ACCESS_KEY`,
`STORAGE_R2_SECRET_KEY`, `STORAGE_R2_PATH_STYLE`, `SETUP_ADMIN_ENABLED`,
`SETUP_ADMIN_SECRET`, `PAYMENT_EXPIRATION_HOURS`, `PAYMENT_REMINDER_HOURS`,
`TOYYIBPAY_SECRET_KEY`, `TOYYIBPAY_CATEGORY_CODE`, `TOYYIBPAY_RETURN_URL`,
`TOYYIBPAY_CALLBACK_URL`, `TOYYIBPAY_VERIFY_CALLBACK`, `CORS_ORIGINS`.

`SUPABASE_URL`, `SUPABASE_KEY` and `SUPABASE_SIGNED_URL_EXPIRATION` still appear in the
config and `fly.toml` for the retired Supabase Storage integration. Storage now goes
through `app.storage.provider` (`local`, `r2`, `s3`); treat the Supabase keys as legacy.

## API in one screen

Full reference with request bodies, error shapes and flow examples: [`docs/API.md`](docs/API.md).

| Area | Endpoints |
|---|---|
| Catalog (public) | `GET /api/products`, `GET /api/products/{id}`, `GET /api/products/{id}/images/{name}` |
| Auth | `POST /api/auth/{register,login,google,refresh,setup-admin}`, `GET /api/auth/{me,profile}` |
| Orders | `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{orderNumber}`, `GET /api/orders/my`, `PUT /api/orders`, `DELETE /api/orders`, `POST /api/orders/{orderNumber}/{pay,confirm-price,mock-pay}` |
| Addresses | `GET/POST /api/addresses`, `PUT/DELETE /api/addresses/{id}`, `PATCH /api/addresses/{id}/default` |
| Uploads | `POST /api/files/upload`, `POST /api/files/payment-proof`, `DELETE /api/files`, `GET /api/files/order/{orderId}`, `GET /api/files/order/{orderId}/download`, strip export paths |
| Payments | `POST /api/toyyibpay/create-bill`, `POST /api/webhooks/toyyibpay` |
| Marketing | `GET /api/reviews`, `POST /api/reviews`, `GET /api/testimonials`, `GET /api/print-sizes` |
| Admin API | `/api/admin/**` — orders, stats, users, print sizes, catalog, system |
| Admin UI | `/admin`, `/admin/orders`, `/admin/orders/{id}`, `/admin/users`, `/admin/stats`, `/admin/settings` |

The service publishes its own schema at `/v3/api-docs`, so Swagger UI is the tie-breaker
when a field or status code is in doubt.

## Data model

Tables created by Flyway (`src/main/resources/db/migration`, V1-V29):

| Table | Purpose |
|---|---|
| `users` | Accounts and roles (`CUSTOMER`, `AFFILIATE`, `PACKER`, `MARKETING`, `ADMIN`) |
| `addresses` | Saved customer addresses, one default per user |
| `products`, `product_variants`, `product_packages`, `product_meta`, `product_aliases` | Catalog: product → style variant → size package (pricing tiers), marketing metadata, legacy URL aliases |
| `print_sizes`, `print_size_pricing_tiers` | Legacy size catalogue still used by older orders and admin size settings |
| `orders`, `order_items`, `order_status_history` | Order header, line snapshots (variant, package, price, strip design state) and audit trail |
| `strip_checkout_requests` | Idempotency records for strip-design checkouts |
| `reviews`, `testimonials` | Marketing content |

Money columns are `numeric(10,2)`. Order items snapshot the catalog selection at checkout,
so later price edits never rewrite history.

## Flows

**Guest checkout.** `POST /api/orders` with `Idempotency-Key` → `DRAFT` order, `expiresAt`
(default 24 h) and a one-time `uploadToken` (hashed at rest, default 168 h). The customer
uploads photos with that token, pays through ToyyibPay (`POST /api/orders/{n}/pay`), and
the webhook moves the order to `PENDING`. `DraftCleanupService` expires unpaid drafts every
10 minutes.

**Authenticated checkout.** Same endpoint with a JWT → `PENDING`, no upload token. If the
server total no longer matches what the customer saw, the call returns `409 PRICE_CHANGED`
with `currentTotal`; the client calls `POST /api/orders/{n}/confirm-price` and retries.

**Bank transfer.** `paymentMethod: bank_transfer` accepts a proof image through
`POST /api/files/payment-proof`; staff verify it with
`POST /api/admin/orders/{orderNumber}/verify-payment`.

**Strip designs.** Strip products reserve assets during checkout (schema in
`POST /api/orders` → `stripDesign`), uploads go through the same `/api/files/upload` with
an `assetId`, and sellers export finished sheets or individual slots through
`/api/files/order/{orderId}/items/{itemId}/...`.

## Security

- JWT bearer tokens, 24 h access / 7 day refresh; role checks by URL rule in
  `config/SecurityConfig.java` plus `@PreAuthorize` on handlers.
- Public: catalog reads, auth entry points, order creation and single-segment order
  lookup (email-verified), uploads (token-gated for guests), webhooks, health, Swagger.
- `/api/admin/system/**` is `ADMIN`; the rest of `/api/admin/**` allows
  `ADMIN`/`MARKETING`/`PACKER`; user and print-size management is `ADMIN` only.
- Uploads are validated by magic bytes, size, pixel count and decoded memory, and
  anonymous uploads are rate limited (10 per 10 minutes per IP/order).
- Known gaps (documented in [`docs/API.md`](docs/API.md#known-authorization-gaps)):
  `GET /api/orders?userId=` is public, `GET /api/reviews` exposes reviewer UUIDs,
  `POST /api/reviews` trusts a body-supplied author, and Swagger is public in prod.

## Testing

```bash
mvn test
```

12 test classes / 62 tests cover file authorization and fulfillment, catalog mapping,
pricing tiers, image covers, product images, order fulfillment and login error handling.
They run against in-memory H2 in PostgreSQL mode, so no database is needed.

## Deployment

Production runs on Fly.io: app `polaroid-glossy-backend`, region `sin`, two
`shared-cpu-1x` machines with 512 MB, health check `GET /api/health`.

```bash
scripts/deploy-prod.sh --check   # preflight: credentials, branch, unpushed commits
scripts/deploy-prod.sh           # flyctl deploy --remote-only, then polls /api/health
```

GitHub Actions runs `ci.yml` on every push and PR to `main`, and `fly-deploy.yml` deploys
only after CI succeeds for the same commit (`workflow_run`). See
[`docs/production-workflow.md`](docs/production-workflow.md).

## Documentation index

| Document | Contents |
|---|---|
| [`docs/API.md`](docs/API.md) | Full endpoint reference, auth, errors, flow examples |
| [`backend_architecture.md`](backend_architecture.md) | Module map, data model, security, flows |
| [`QUICKSTART.md`](QUICKSTART.md) | Local setup and first run |
| [`docs/fly-backend-deployment.md`](docs/fly-backend-deployment.md) | Fly app, secrets, deploy and smoke checks |
| [`docs/production-workflow.md`](docs/production-workflow.md) | Branch, release and post-deploy process |
| [`docs/production-security-checklist.md`](docs/production-security-checklist.md) | Pre-deploy security checklist |
| [`docs/frontend-security-handoff.md`](docs/frontend-security-handoff.md) | Frontend-facing API/auth changes |
| [`docs/from-front-end-security-handoff.md`](docs/from-front-end-security-handoff.md) | Earlier handoff note, kept for history |
| [`docs/catalog/FRONTEND-INTEGRATION.md`](docs/catalog/FRONTEND-INTEGRATION.md) | Catalog API guide for the storefront |
| [`docs/catalog/PRODUCT-IMAGES.md`](docs/catalog/PRODUCT-IMAGES.md) | Product image and cover-image rules |
| [`docs/backend-variant-image-cover/implementation-plan.md`](docs/backend-variant-image-cover/implementation-plan.md) | Per-style cover image design notes |
| [`docs/strip-docmentation/strip-design-fulfillment-handoff.md`](docs/strip-docmentation/strip-design-fulfillment-handoff.md) | Strip design upload and export contract |
| [`PLAN-backend-product-api.md`](PLAN-backend-product-api.md), [`PLAN-product-catalog-api.md`](PLAN-product-catalog-api.md) | Historical implementation plans |

## Repository layout

```
src/main/java/com/polaroid/
  config/       security, CORS, JPA auditing, exception handler, seed data
  controller/   17 REST controllers + Thymeleaf admin pages
  dto/          request/response records and mappers
  exception/    domain exceptions mapped by GlobalExceptionHandler
  model/        JPA entities + enums
  repository/   Spring Data repositories
  security/     JWT filter and user details wiring
  service/      22 services (orders, catalog, payments, files, strip, email, stats)
  storage/      StorageService with local / R2 / S3 strategies
src/main/resources/
  db/migration/ 30 Flyway SQL migrations (V1..V29)
  templates/    Thymeleaf admin pages and email templates
frontend/       Next.js storefront/dashboard, deployed separately (Vercel)
scripts/        deploy-prod.sh, run-backend-dev.sh
docs/           API, deployment, catalog and security documentation
```

## License

MIT License
