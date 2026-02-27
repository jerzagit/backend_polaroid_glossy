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
| Language | Java | 17+ |
| Build Tool | Maven | 3.9.x |
| Database | PostgreSQL | 15+ (Supabase) |
| ORM | Spring Data JPA | - |
| Security | Spring Security + JWT | - |
| File Storage | Supabase Storage | - |
| Payment | ToyyibPay | API v1 |

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
# Using Maven (dev profile — Flyway disabled, Hibernate manages schema)
./apache-maven-3.9.6/bin/mvn.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=dev"
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

## API Endpoints

### Authentication

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/auth/register` | Register new customer | Public |
| POST | `/api/auth/login` | Login, returns JWT | Public |
| POST | `/api/auth/refresh` | Refresh JWT token | Auth |
| GET | `/api/auth/me` | Get current user | Auth |

### Orders (Public)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/orders` | Create new order | Auth |
| GET | `/api/orders/{orderNumber}` | Get order by number | Public |
| GET | `/api/orders/my` | Get my orders | Auth |
| POST | `/api/orders/{orderNumber}/pay` | Initiate payment | Auth |

### Orders (Admin)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/admin/orders` | List orders (paginated) | Packer+ |
| GET | `/api/admin/orders/{id}` | Get order details | Packer+ |
| PATCH | `/api/admin/orders/{id}/status` | Update status | Marketing+ |
| PATCH | `/api/admin/orders/{id}/tracking` | Add tracking # | Packer+ |
| POST | `/api/admin/orders/{id}/notes` | Add internal notes | Marketing+ |

### Stats & Analytics

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/admin/stats/overview` | Dashboard stats | Marketing+ |
| GET | `/api/admin/stats/orders-by-status` | Orders by status | Marketing+ |
| GET | `/api/admin/stats/top-sizes` | Best selling sizes | Marketing+ |
| GET | `/api/admin/stats/by-state` | Orders by state | Admin only |

### System (Admin Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/system/storage` | Storage usage |
| GET | `/api/admin/system/database` | Database health |
| GET | `/api/admin/system/payment-costs` | Payment fees |
| GET | `/api/admin/system/server` | Server metrics |

### File Management

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/files/upload` | Upload image | Auth |
| DELETE | `/api/files/{key}` | Delete image | Auth |
| GET | `/api/files/order/{orderId}/download` | Download images | Packer+ |

### Webhooks

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/webhooks/toyyibpay` | Payment callback |

---

## User Roles & Permissions

| Feature | Customer | Packer | Marketing | Admin |
|---------|:--------:|:------:|:---------:|:-----:|
| Place Order | ✅ | ✅ | ✅ | ✅ |
| View Own Orders | ✅ | ❌ | ✅ | ✅ |
| View All Orders | ❌ | ✅ | ✅ | ✅ |
| Update Status | ❌ | 🚫P/D | ✅ | ✅ |
| Add Tracking | ❌ | ✅ | ✅ | ✅ |
| View Stats | ❌ | ❌ | 🚫NoRev | ✅ |
| System Info | ❌ | ❌ | ❌ | ✅ |

*🚫P/D = Posted/Delivered only  
🚫NoRev = Can see orders but not revenue*

---

## Database Schema

### Tables

1. **users** - User accounts with roles (CUSTOMER, AFFILIATE, PACKER, MARKETING, ADMIN)
2. **orders** - Order records with payment status
3. **order_items** - Individual items in each order
4. **order_status_history** - Audit trail of status changes
5. **print_sizes** - Available photo print sizes (reference data)

### Materialized Views

- `mv_daily_sales` - Daily sales summary
- `mv_orders_by_status` - Orders grouped by status
- `mv_top_sizes` - Best selling print sizes

---

## Security

### JWT Authentication
- Access token: 24 hours expiration
- Refresh token: 7 days expiration
- Password hashing: BCrypt

### Role-Based Access
- All endpoints protected by role checks
- Packer can only update to POSTED/DELIVERED status
- Marketing can view stats but not revenue

---

## Payment Integration

### ToyyibPay Flow

1. Customer creates order → `POST /api/orders`
2. Customer initiates payment → `POST /api/orders/{orderNumber}/pay`
3. Redirect to ToyyibPay checkout
4. After payment, ToyyibPay calls webhook → `POST /api/webhooks/toyyibpay`
5. Order payment status updated to PAID

### Payment Status
- `PENDING` - Order created, awaiting payment
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

### Supported Operations
- Upload images per order
- List order images
- Download all order images as ZIP
- Delete individual images

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
