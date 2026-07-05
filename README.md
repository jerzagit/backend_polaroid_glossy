# Polaroid Glossy Backend

REST API backend for e-commerce platform specializing in polaroid photo printing.

## Table of Contents
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Security](#security)
- [Payment Integration](#payment-integration)
- [File Storage](#file-storage)

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Spring Boot | 3.4.x |
| Language | Java | 21 (LTS) |
| Build Tool | Maven | 3.9.x |
| Database | PostgreSQL | 15+ (Supabase) |
| ORM | Spring Data JPA | - |
| Security | Spring Security + JWT | - |
| File Storage | Supabase Storage (R2-compatible) | - |
| Payment | ToyyibPay | API v1 |
| Scheduling | Spring @Scheduled | Draft expiry cleanup every 10 min |
| Migration | Flyway | 12 migrations applied |

---

## Project Structure

```
polaroid-backend/
├── src/main/
│   ├── java/com/polaroid/
│   │   ├── PolaroidApplication.java
│   │   ├── config/
│   │   │   ├── CorsConfig.java
│   │   │   ├── SecurityConfig.java
│   │   │   ├── SupabaseConfig.java
│   │   │   └── RestTemplateConfig.java
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   ├── OrderController.java
│   │   │   ├── AdminController.java
│   │   │   ├── FileController.java
│   │   │   ├── WebhookController.java
│   │   │   └── SystemController.java
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   ├── OrderService.java
│   │   │   ├── PaymentService.java
│   │   │   ├── FileService.java
│   │   │   ├── StatsService.java
│   │   │   ├── SystemService.java
│   │   │   └── UserService.java
│   │   ├── repository/
│   │   ├── model/
│   │   ├── dto/
│   │   ├── security/
│   │   └── exception/
│   └── resources/
│       ├── application.yml
│       ├── db/migration/
│       └── templates/
└── pom.xml
```

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker (for PostgreSQL)
- Node.js 18+ (for frontend)

### Quick Start

#### 1. Start PostgreSQL (Using Docker)
```bash
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password -e POSTGRES_DB=polaroid --name polaroid-postgres postgres:15
```

If another local project already uses port `5432`, the project run script starts `polaroid-postgres-dev` on port `5433` instead.

#### 2. Configure Environment
Edit `.env.dev` file with your database credentials:
```bash
# Database (Local PostgreSQL)
DATABASE_URL=jdbc:postgresql://localhost:5432/polaroid
DB_USERNAME=postgres
DB_PASSWORD=password

# JWT
JWT_SECRET=dev-secret-key-minimum-32-characters-long-for-development
```

#### 3. Run Backend
```bash
# Uses Java 17 for this project only and starts the dev profile
./scripts/run-backend-dev.sh
```

The API will be available at `http://localhost:8080`

#### 4. Create Admin User
Call the setup API to create an admin:
```
POST http://localhost:8080/api/auth/setup-admin?secret=admin-secret-2024

Body:
{
  "email": "admin@polaroid.com",
  "password": "admin123",
  "name": "Admin User",
  "phone": "+60123456789"
}
```

#### 5. Login
```
POST http://localhost:8080/api/auth/login

Body:
{
  "email": "admin@polaroid.com",
  "password": "admin123"
}
```

### Running with Docker

```bash
# Start PostgreSQL
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password -e POSTGRES_DB=polaroid --name polaroid-postgres postgres:15

# Run backend
mvn spring-boot:run
```

### Alternative: H2 Database (Testing Only)
If you don't want to use Docker/PostgreSQL, you can use H2 in-memory database:

Edit `application-dev.yml`:
```yaml
datasource:
  url: jdbc:h2:mem:polaroiddb
  driver-class-name: org.h2.Driver
  username: sa
  password: 

flyway:
  enabled: false
```

Note: H2 data is lost on restart.

---

## Configuration

### application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/polaroid}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: update

jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-minimum-32-characters-long}
  expiration: 86400000
  refresh-expiration: 604800000

supabase:
  url: ${SUPABASE_URL:https://your-project.supabase.co}
  key: ${SUPABASE_KEY:your-anon-key}
  storage-bucket: polaroid-glossy

toyyibpay:
  secret-key: ${TOYYIBPAY_SECRET_KEY:your-secret-key}
  category-code: ${TOYYIBPAY_CATEGORY_CODE:your-category}
  return-url: ${TOYYIBPAY_RETURN_URL:http://localhost:3000/payment-status}
  callback-url: ${TOYYIBPAY_CALLBACK_URL:http://localhost:8080/api/webhooks/toyyibpay}
  fee-percentage: 2.5

cors:
  allowed-origins: ${CORS_ORIGINS:http://localhost:3000}
```

---

## Authentication

All protected endpoints require `Authorization: Bearer <jwt>` header. Obtain JWT via `POST /api/auth/login`, `/api/auth/register`, or `/api/auth/google`.

## API Reference

### Authentication (`/api/auth`)

#### `POST /api/auth/register`
Register a new customer account.

**Auth:** None

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "John Doe",
  "phone": "+60123456789",
  "affiliateCode": "REF123"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "user@example.com",
  "name": "John Doe",
  "role": "CUSTOMER",
  "expiresIn": 86400000
}
```

---

#### `POST /api/auth/login`
Login with email and password.

**Auth:** None

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "user@example.com",
  "name": "John Doe",
  "role": "CUSTOMER",
  "expiresIn": 86400000
}
```

---

#### `POST /api/auth/google`
Login or register via Google OAuth. Call this after frontend Google sign-in.

**Auth:** None

**Request:**
```json
{
  "email": "user@gmail.com",
  "name": "John Doe",
  "avatarUrl": "https://lh3.googleusercontent.com/..."
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "user@gmail.com",
  "name": "John Doe",
  "role": "CUSTOMER",
  "expiresIn": 86400000
}
```

---

#### `POST /api/auth/refresh`
Refresh an expired JWT.

**Auth:** None (uses refresh token as body)

**Request:**
```
eyJhbGciOiJIUzI1NiJ9...
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "user@example.com",
  "name": "John Doe",
  "role": "CUSTOMER",
  "expiresIn": 86400000
}
```

---

#### `GET /api/auth/me`
Get current authenticated user's profile.

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "name": "John Doe",
  "phone": "+60123456789",
  "avatarUrl": "https://...",
  "role": "CUSTOMER",
  "affiliateCode": "REF123",
  "isActive": true,
  "createdAt": "2026-07-05T10:00:00"
}
```

---

#### `GET /api/auth/profile`
Get extended profile with counts.

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "success": true,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "name": "John Doe",
    "role": "CUSTOMER"
  },
  "orderCount": 5,
  "draftCount": 2,
  "addressCount": 3
}
```

---

#### `POST /api/auth/setup-admin?secret=admin-secret-2024`
Create admin account (only when enabled in config).

**Auth:** None (protected by secret query param)

**Request:**
```json
{
  "email": "admin@polaroid.com",
  "password": "admin123",
  "name": "Admin User",
  "phone": "+60123456789"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "admin@polaroid.com",
  "name": "Admin User",
  "role": "ADMIN"
}
```

---

### Orders (`/api/orders`)

#### `POST /api/orders`
Create a new order. Guest users (no auth) get `DRAFT` status with 24h `expiresAt`. Authenticated users get `PENDING` status.

**Auth:** Optional (guest creates DRAFT, authenticated creates PENDING)

**Request:**
```json
{
  "customerName": "John Doe",
  "customerEmail": "john@example.com",
  "customerPhone": "+60123456789",
  "customerHouseUnitNo": "12-3",
  "customerAddressLine1": "Jalan Ampang",
  "customerAddressLine2": "Kuala Lumpur",
  "customerPostcode": "50450",
  "customerCity": "Kuala Lumpur",
  "customerState": "Kuala Lumpur",
  "customerCountry": "Malaysia",
  "affiliateCode": "REF123",
  "notes": "Handle with care",
  "items": [
    {
      "sizeId": "4R",
      "quantity": 2,
      "imageUrls": ["https://...", "https://..."],
      "customTexts": ["Happy Birthday!", ""]
    }
  ]
}
```

**Response (200) - Guest (DRAFT):**
```json
{
  "success": true,
  "order": {
    "id": "uuid",
    "orderNumber": "PG12345678A01",
    "status": "DRAFT",
    "paymentStatus": "PENDING",
    "subtotal": 10.00,
    "shipping": 5.00,
    "total": 15.00,
    "expiresAt": "2026-07-06T10:00:00",
    "draftExpiredAt": null,
    "items": [
      {
        "sizeId": "4R",
        "sizeName": "4R",
        "quantity": 2,
        "unitPrice": 5.00,
        "totalPrice": 10.00
      }
    ],
    "createdAt": "2026-07-05T10:00:00"
  }
}
```

**Response (200) - Authenticated (PENDING):**
```json
{
  "success": true,
  "order": {
    "id": "uuid",
    "orderNumber": "PG12345678A01",
    "status": "PENDING",
    "paymentStatus": "PENDING",
    "subtotal": 10.00,
    "shipping": 5.00,
    "total": 15.00,
    "expiresAt": null,
    "items": [...],
    "createdAt": "2026-07-05T10:00:00"
  }
}
```

---

#### `GET /api/orders?orderNumber=PG12345678A01`
Get order by order number. Accessible by order owner (auth) or via email param.

**Auth:** Optional (adds owner verification if provided)

**Response (200):**
```json
{
  "success": true,
  "orders": [
    {
      "id": "uuid",
      "orderNumber": "PG12345678A01",
      "status": "PENDING",
      "paymentStatus": "PAID",
      "subtotal": 10.00,
      "total": 15.00,
      "items": [...],
      "statusHistory": [
        { "status": "DRAFT", "message": "Order created", "createdAt": "..." },
        { "status": "PENDING", "message": "Payment completed", "createdAt": "..." }
      ],
      "createdAt": "2026-07-05T10:00:00"
    }
  ]
}
```

---

#### `GET /api/orders/{orderNumber}?email=customer@example.com`
Get single order by number. Email param allows guest access.

**Auth:** Optional

**Response (200):**
```json
{
  "id": "uuid",
  "orderNumber": "PG12345678A01",
  "status": "PENDING",
  "paymentStatus": "PAID",
  "items": [...],
  "createdAt": "2026-07-05T10:00:00"
}
```

---

#### `GET /api/orders/my?page=0&size=20&sortBy=createdAt&sortDir=desc&status=DRAFT`
Get paginated orders for the authenticated user. Filter by status optionally.

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "success": true,
  "orders": [
    {
      "id": "uuid",
      "orderNumber": "PG12345678A01",
      "status": "DRAFT",
      "expiresAt": "2026-07-06T10:00:00",
      "total": 15.00,
      "items": [...],
      "createdAt": "2026-07-05T10:00:00"
    }
  ],
  "totalPages": 1,
  "totalElements": 3,
  "page": 0,
  "size": 20
}
```

---

#### `PUT /api/orders`
Update an existing order (e.g., shipping address, items). Requires auth.

**Auth:** `Authorization: Bearer <jwt>`

**Request:**
```json
{
  "orderNumber": "PG12345678A01",
  "customerName": "John Updated",
  "customerPhone": "+60198765432",
  "items": [...]
}
```

**Response (200):**
```json
{
  "success": true,
  "order": { "...": "..." }
}
```

---

#### `DELETE /api/orders?orderId=uuid`
Cancel an order by ID. Requires auth.

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "success": true,
  "message": "Order cancelled",
  "order": { "...": "..." }
}
```

---

#### `POST /api/orders/{orderNumber}/pay`
Initiate ToyyibPay payment for an order. Transitions DRAFT→PENDING on completion via webhook.

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "paymentUrl": "https://toyyibpay.com/BILLCODE123"
}
```

---

#### `POST /api/orders/{orderNumber}/mock-pay`
Mock payment for development (only when `app.mock-payments.enabled=true`).

**Auth:** `Authorization: Bearer <jwt>`

**Request (optional):**
```json
{
  "status": "PAID"
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "status": "PENDING",
  "paymentStatus": "PAID",
  "...": "..."
}
```

---

#### `GET /api/orders/payment-return?order_id=xyz`
Handle ToyyibPay return redirect after payment.

**Auth:** None

**Response (200):**
```json
{
  "status": "1",
  "billcode": "BILL123",
  "order_id": "PG12345678A01",
  "message": "Payment successful"
}
```

---

### Addresses (`/api/addresses`)

All address endpoints require authentication. Max 10 addresses per user. First address created is auto-set as default.

#### `GET /api/addresses`
List all saved addresses for the authenticated user (default first, then by creation date).

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "success": true,
  "addresses": [
    {
      "id": "uuid",
      "label": "Home",
      "name": "John Doe",
      "phone": "+60123456789",
      "houseUnitNo": "12-3",
      "addressLine1": "Jalan Ampang",
      "addressLine2": "",
      "city": "Kuala Lumpur",
      "state": "Kuala Lumpur",
      "postalCode": "50450",
      "country": "Malaysia",
      "isDefault": true,
      "createdAt": "2026-07-05T10:00:00"
    }
  ]
}
```

---

#### `POST /api/addresses`
Create a new address. If it's the first address, it becomes default automatically.

**Auth:** `Authorization: Bearer <jwt>`

**Request:**
```json
{
  "label": "Home",
  "name": "John Doe",
  "phone": "+60123456789",
  "houseUnitNo": "12-3",
  "addressLine1": "Jalan Ampang",
  "addressLine2": "Kuala Lumpur",
  "city": "Kuala Lumpur",
  "state": "Kuala Lumpur",
  "postalCode": "50450",
  "country": "Malaysia",
  "isDefault": true
}
```

**Response (201):**
```json
{
  "success": true,
  "address": {
    "id": "uuid",
    "label": "Home",
    "name": "John Doe",
    "isDefault": true,
    "createdAt": "2026-07-05T10:00:00"
  }
}
```

---

#### `PUT /api/addresses/{id}`
Update an existing address. Only the owner can update.

**Auth:** `Authorization: Bearer <jwt>`

**Request:** Same body as POST.

**Response (200):**
```json
{
  "success": true,
  "address": { "...": "..." }
}
```

---

#### `DELETE /api/addresses/{id}`
Delete an address. Only the owner can delete. Cannot delete if it's the last address (keeps at least 1).

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "success": true,
  "message": "Address deleted"
}
```

---

#### `PATCH /api/addresses/{id}/default`
Set an address as the default. All other addresses for this user get `isDefault: false`.

**Auth:** `Authorization: Bearer <jwt>`

**Response (200):**
```json
{
  "success": true,
  "address": {
    "id": "uuid",
    "isDefault": true,
    "...": "..."
  }
}
```

---

### Print Sizes (`/api/print-sizes`)

#### `GET /api/print-sizes`
Get all active print sizes (prices come from DB, not hardcoded).

**Auth:** None

**Response (200):**
```json
[
  {
    "id": "2R",
    "name": "2R",
    "displayName": "2R (2.5 x 3.5 inches)",
    "width": 2.5,
    "height": 3.5,
    "price": 3.00,
    "description": "Wallet size - Perfect for keepsakes",
    "isActive": true
  },
  {
    "id": "3R",
    "name": "3R",
    "displayName": "3R (3.5 x 5 inches)",
    "width": 3.5,
    "height": 5.0,
    "price": 4.00,
    "description": "Standard photo size - Great for albums",
    "isActive": true
  },
  {
    "id": "4R",
    "name": "4R",
    "displayName": "4R (4 x 6 inches)",
    "width": 4.0,
    "height": 6.0,
    "price": 5.00,
    "description": "Most popular - Classic polaroid style",
    "isActive": true
  },
  {
    "id": "A4",
    "name": "A4",
    "displayName": "A4 (8.3 x 11.7 inches)",
    "width": 8.3,
    "height": 11.7,
    "price": 15.00,
    "description": "Poster size - Perfect for displays",
    "isActive": true
  }
]
```

---

### File Upload (`/api/files`)

#### `POST /api/files/upload`
Upload an image to an order. Authenticated users can always upload. Guest uploads require a valid order in `DRAFT` status (uploads blocked if `EXPIRED` or already `PENDING`).

**Auth:** Optional (authenticated bypasses payment check; guests need valid DRAFT order)

**Request:** `multipart/form-data`
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| file | File | ✅ | Image file (JPEG, PNG, max 10MB) |
| orderId | String | ✅ | Order UUID |
| customerEmail | String | For guest | Required if no auth |
| uploadToken | String | For guest | Upload token from order |
| orderItemId | String | No | Link to specific item |

**Response (200):**
```json
{
  "success": true,
  "url": "https://...",
  "key": "original/{orderId}/{uuid}.jpg",
  "signedUrl": "https://...?token=..."
}
```

---

#### `DELETE /api/files?key=original/{orderId}/{uuid}.jpg`
Delete a file by key. Staff only.

**Auth:** `Authorization: Bearer <jwt>` (Packer+)

**Response (204):** No content

---

#### `GET /api/files/order/{orderId}`
List files for an order. Admin only.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

**Response (200):**
```json
[
  { "key": "original/{orderId}/{uuid}.jpg", "url": "https://..." },
  { "key": "original/{orderId}/{uuid2}.jpg", "url": "https://..." }
]
```

---

#### `GET /api/files/order/{orderId}/download`
Download all order images as ZIP.

**Auth:** `Authorization: Bearer <jwt>` (Packer+)

**Response (200):** `application/zip` binary

---

### Admin (`/api/admin`)

#### `GET /api/admin/stats/overview`
Dashboard statistics with optional filters.

**Auth:** `Authorization: Bearer <jwt>` (Marketing+). Marketing cannot see revenue.

**Query Params:** `status`, `paymentStatus`, `customerState`, `fromDate`, `toDate`, `orderReference`, `customerEmail`, `customerPhone`

**Response (200):**
```json
{
  "totalOrders": 150,
  "totalRevenue": 5000.00,
  "totalOrdersPending": 12,
  "totalOrdersPaid": 100,
  "totalOrdersPosted": 20,
  "totalOrdersDelivered": 15,
  "totalOrdersCancelled": 3
}
```

---

#### `GET /api/admin/stats/orders-by-status`
Order count grouped by status.

**Auth:** `Authorization: Bearer <jwt>` (Marketing+)

**Response (200):**
```json
{
  "PENDING": 12,
  "PAID": 100,
  "PROCESSING": 5,
  "POSTED": 20,
  "ON_DELIVERY": 3,
  "DELIVERED": 7,
  "CANCELLED": 3
}
```

---

#### `GET /api/admin/stats/top-sizes`
Best selling print sizes.

**Auth:** `Authorization: Bearer <jwt>` (Marketing+)

**Response (200):**
```json
[
  ["4R", 500],
  ["3R", 300],
  ["A4", 100],
  ["2R", 50]
]
```

---

#### `GET /api/admin/stats/by-state`
Orders grouped by customer state. Admin only.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

**Response (200):**
```json
[
  ["Selangor", 45],
  ["Kuala Lumpur", 30],
  ["Johor", 20]
]
```

---

#### `GET /api/admin/orders`
List all orders with filtering and pagination.

**Auth:** `Authorization: Bearer <jwt>` (Packer+)

**Query Params:** `status`, `paymentStatus`, `customerState`, `fromDate`, `toDate`, `orderReference`, `customerEmail`, `customerPhone`, `page`, `size`, `sortBy`, `sortDir`

**Response (200):** Spring Data `Page<OrderResponse>`:
```json
{
  "content": [ ...orders... ],
  "totalPages": 5,
  "totalElements": 100,
  "pageable": { ... },
  "number": 0,
  "size": 20
}
```

---

#### `GET /api/admin/orders/{id}`
Get single order by ID.

**Auth:** `Authorization: Bearer <jwt>` (Packer+)

---

#### `PATCH /api/admin/orders/{id}/status`
Update order status.

**Auth:** `Authorization: Bearer <jwt>` (Marketing+; Packer restricted to POSTED/DELIVERED)

**Request:**
```json
{
  "status": "PROCESSING",
  "message": "Packing in progress"
}
```

---

#### `PATCH /api/admin/orders/{id}/tracking?trackingNumber=POS123456`
Add tracking number.

**Auth:** `Authorization: Bearer <jwt>` (Packer+)

---

#### `PATCH /api/admin/orders/{id}/payment-status`
Update payment status manually (admin).

**Auth:** `Authorization: Bearer <jwt>` (Marketing+)

**Request:**
```json
{
  "paymentStatus": "PAID"
}
```

---

#### `POST /api/admin/orders/{id}/notes?notes=Urgent+order`
Add internal notes.

**Auth:** `Authorization: Bearer <jwt>` (Packer+)

---

#### `GET /api/admin/users`
List all users.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

**Query Params:** `role`, `search`, `page`, `size`

---

#### `GET /api/admin/users/{id}`
Get user by ID.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

---

#### `PATCH /api/admin/users/{id}/role`
Update user role.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

**Request:**
```json
{
  "role": "PACKER"
}
```

---

#### `GET /api/admin/settings/print-sizes`
List all print sizes (active + inactive).

**Auth:** `Authorization: Bearer <jwt>` (Admin)

---

#### `POST /api/admin/settings/print-sizes`
Create new print size.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

---

#### `PATCH /api/admin/settings/print-sizes/{id}`
Update print size.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

---

#### `DELETE /api/admin/settings/print-sizes/{id}`
Delete print size.

**Auth:** `Authorization: Bearer <jwt>` (Admin)

---

### Webhooks (`/api/webhooks`)

#### `POST /api/webhooks/toyyibpay`
ToyyibPay payment callback. Called by ToyyibPay after customer completes payment. Transitions DRAFT→PENDING if status is PAID.

**Auth:** Verified by ToyyibPay hash

**Query Params:** `refno`, `order_id`, `billcode`, `status`, `amount`, `hash`

**Response (200):**
```json
{
  "status": "paid",
  "message": "Payment callback processed"
}
```

---

## Order Status Lifecycle

```
Guest Order:   DRAFT ──(pay via webhook)──→ PENDING ──→ PROCESSING ──→ POSTED ──→ ON_DELIVERY ──→ DELIVERED
                                              ↑               │
                                           (24h expires)    CANCELLED / REFUNDED
                                              ↓
                                          EXPIRED (cron cleanup every 10 min)

Auth Order:    PENDING ──(pay)──→ PENDING ──→ ...
                ↑                  ↑
           (direct)        (same as above)
```

- **DRAFT**: Guest orders start here. Images can be uploaded. Expires in 24h.
- **EXPIRED**: Set by `DraftCleanupService` cron job (every 10 min). Uploads blocked.
- **PENDING**: Awaiting payment. Authenticated orders start here. DRAFT transitions here on payment.
- **PROCESSING / POSTED / ON_DELIVERY / DELIVERED / CANCELLED / REFUNDED**: Normal fulfillment flow.

---

## User Roles & Permissions

| Feature | Customer | Affiliate | Packer | Marketing | Admin |
|---------|:--------:|:---------:|:------:|:---------:|:-----:|
| **Orders** | | | | | |
| Place Order | ✅ | ✅ | ✅ | ✅ | ✅ |
| View Own Orders | ✅ | Own | ❌ | ✅ | ✅ |
| View All Orders | ❌ | ❌ | ✅ | ✅ | ✅ |
| Create DRAFT (Guest) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Order Management** | | | | | |
| Update Status | ❌ | ❌ | 🚫P/D | ✅ | ✅ |
| Add Tracking | ❌ | ❌ | ✅ | ✅ | ✅ |
| Payment Status | ❌ | ❌ | ❌ | ✅ | ✅ |
| Add Notes | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Addresses** | | | | | |
| CRUD Addresses | ✅ | ✅ | ✅ | ✅ | ✅ |
| Set Default | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Files** | | | | | |
| Upload (Own Order) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Upload (DRAFT) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Download ZIP | ❌ | ❌ | ✅ | ✅ | ✅ |
| Delete Files | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Admin** | | | | | |
| Stats Overview | ❌ | ❌ | ❌ | 🚫NoRev | ✅ |
| System Info | ❌ | ❌ | ❌ | ❌ | ✅ |
| Manage Users | ❌ | ❌ | ❌ | ❌ | ✅ |
| Print Sizes | ❌ | ❌ | ❌ | ❌ | ✅ |

*🚫P/D = Posted/Delivered only  
🚫NoRev = Can see order counts but not revenue*

---

## Database Schema

### Tables

1. **users** - User accounts with roles (CUSTOMER, AFFILIATE, PACKER, MARKETING, ADMIN)
2. **orders** - Order records with `status` (DRAFT, PENDING, PROCESSING, POSTED, ON_DELIVERY, DELIVERED, CANCELLED, REFUNDED, EXPIRED), `payment_status`, `expires_at`, `draft_expired_at`
3. **order_items** - Individual items in each order (size, qty, price, images)
4. **order_status_history** - Audit trail of status changes
5. **addresses** - Saved user addresses (max 10/user, single `is_default` flag)
6. **print_sizes** - Available photo print sizes (reference data, prices from DB)
7. **reviews** - Product reviews
8. **testimonials** - Site testimonials

### Materialized Views

- `mv_daily_sales` - Daily sales summary
- `mv_orders_by_status` - Orders grouped by status
- `mv_top_sizes` - Best selling print sizes

---

## Flyway Migrations

| Version | Description |
|---------|-------------|
| V1 | Initial schema (users, orders, order_items, order_status_history, print_sizes, reviews, testimonials, materialized views) |
| V2 | Seed data (default print sizes) |
| V3 | Add order delivery address columns |
| V4 | Add customer_city column |
| V5 | Add print_size tag + testimonials/reviews |
| V6 | Fix reviews/testimonials ID type |
| V7 | Add order upload token |
| V8 | Add DRAFT/EXPIRY columns (expires_at, draft_expired_at) + addresses table |
| V9 | Add audit columns (created_by, updated_by) to addresses |
| V10 | Update orders status check constraint (add DRAFT, EXPIRED) |
| V11 | Update order_status_history status check constraint |

---

## Security

### JWT Authentication
- Access token: 24 hours expiration
- Refresh token: 7 days expiration
- Password hashing: BCrypt
- Google OAuth users are auto-created via `POST /api/auth/google`

### Endpoint Security
- Public: `/api/auth/login`, `/api/auth/register`, `/api/auth/google`, `/api/auth/setup-admin`, `/api/webhooks/**`, `/api/print-sizes`, `/swagger-ui/**`, `/v3/api-docs/**`
- Authenticated: `/api/auth/me`, `/api/auth/profile`, `/api/addresses/**`, `/api/orders/my`, `/api/files/upload`
- Staff (Packer+): `/api/admin/orders/**`, `/api/files/**`
- Marketing+: `/api/admin/stats/**` (no revenue for Marketing)
- Admin: `/api/admin/users/**`, `/api/admin/settings/**`, `/api/admin/system/**`

---

## Payment Integration

### ToyyibPay Flow

1. Customer creates order → `POST /api/orders` (DRAFT for guests, PENDING for logged-in)
2. Customer uploads images → `POST /api/files/upload` (allowed for DRAFT)
3. Customer initiates payment → `POST /api/orders/{orderNumber}/pay`
4. Frontend redirects to ToyyibPay checkout URL
5. Customer pays on ToyyibPay
6. ToyyibPay calls webhook → `POST /api/webhooks/toyyibpay`
7. Backend verifies hash, updates status → DRAFT→PENDING (clears expiresAt), payment_status→PAID
8. Frontend polls `GET /api/orders/{orderNumber}` to detect status change

### Guest Draft → Paid Flow
- Guest creates order → `DRAFT` with 24h `expiresAt`
- Guest uploads photos (allowed while DRAFT)
- Guest pays → webhook transitions to `PENDING`, clears `expiresAt`
- If 24h passes without payment → `DRAFT` → `EXPIRED` (cron every 10 min), images orphaned

### Payment Status
- `PENDING` - Awaiting payment
- `PAID` - Payment successful
- `FAILED` - Payment failed

---

## File Storage

### Supabase Storage Structure

```
polaroid-glossy/
└── original/
    └── {orderId}/
        └── {uuid}.jpg
```

### Upload Rules
- Authenticated user: can always upload to their own orders
- Guest user: can only upload to orders in `DRAFT` status
- Uploads blocked if order is `EXPIRED` or status beyond `DRAFT` (for guests)

### Supported Operations
- Upload images per order
- List order images (Admin)
- Download all order images as ZIP (Packer+)
- Delete individual images (Marketing+)

---

## Scheduled Jobs

| Job | Schedule | Description |
|-----|----------|-------------|
| `DraftCleanupService.cleanupExpiredDrafts()` | Every 10 min | Marks DRAFT orders past `expires_at` as EXPIRED |

---

## Deployment

### Production (Fly.io)
- App: `polaroid-glossy-backend`
- Region: `sin` (Singapore)
- VM: 512MB shared CPU
- URL: `https://polaroid-glossy-backend.fly.dev`

### Deploy
```bash
flyctl deploy --app polaroid-glossy-backend
```

### Logs
```bash
flyctl logs --app polaroid-glossy-backend --no-tail
```

---

## Development

### Run Tests
```bash
mvn test
```

### Build
```bash
mvn clean package
```

### Run JAR
```bash
java -jar target/polaroid-backend-1.0.0.jar
```

---

## License

MIT License
