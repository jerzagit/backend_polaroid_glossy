# Backend Architecture - Polaroid Glossy

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Architecture Diagram](#3-architecture-diagram)
4. [Database Schema](#4-database-schema)
5. [API Endpoints](#5-api-endpoints)
6. [User Roles & Permissions](#6-user-roles--permissions)
7. [Security & Authentication](#7-security--authentication)
8. [File Storage (Supabase)](#8-file-storage-supabase)
9. [Payment Integration (ToyyibPay)](#9-payment-integration-toyyibpay)
10. [Project Structure](#10-project-structure)
11. [Configuration](#11-configuration)
12. [Implementation Phases](#12-implementation-phases)

---

## 1. Project Overview

**Project Name:** Polaroid Glossy Backend  
**Purpose:** REST API backend for e-commerce platform specializing in polaroid photo printing  
**Port:** 8080 (default Spring Boot)
**Frontend:** Next.js 14 running on port 3000+ (auto-assigned; 3004 in dev if lower ports are occupied)

---

## 2. Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Spring Boot | 3.4.x (Latest) |
| Language | Java | 21 (LTS) |
| Build Tool | Maven | 3.9.x |
| Database | PostgreSQL | 15+ (Supabase) |
| ORM | Spring Data JPA | - |
| Security | Spring Security + JWT | - |
| File Storage | Supabase Storage | - |
| Payment | ToyyibPay | API v1 |
| Documentation | SpringDoc OpenAPI | 2.5.x |
| Validation | Jakarta Validation | - |

### Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Supabase -->
    <dependency>
        <groupId>io.supabase</groupId>
        <artifactId>supabase-java</artifactId>
        <version>0.1.0</version>
    </dependency>
    
    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
    
    <!-- PDF Generation (for invoices) -->
    <dependency>
        <groupId>com.itextpdf</groupId>
        <artifactId>itext7-core</artifactId>
        <version>8.0.5</version>
        <type>pom</type>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 3. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           FRONTEND (Next.js)                            │
│                         http://localhost:3000                           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP/REST
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT BACKEND                                 │
│                       http://localhost:8080                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │  CONTROLLER │  │   SERVICE   │  │  REPOSITORY │  │    MODEL    │  │
│  │             │  │             │  │             │  │             │  │
│  │ AuthCtlr     │  │ AuthService  │  │ UserRepo     │  │ User         │  │
│  │ OrderCtlr    │  │ OrderService │  │ OrderRepo    │  │ Order        │  │
│  │ AddressCtlr  │  │ AddressSvc   │  │ AddressRepo  │  │ Address      │  │
│  │ AdminCtlr    │  │ PaymentSvc   │  │ OrderItemR   │  │ OrderItem    │  │
│  │ FileCtlr     │  │ FileService  │  │ StatsRepo    │  │ StatusHist   │  │
│  │ SystemCtlr   │  │ SystemSvc    │  │              │  │ SystemInfo   │  │
│  │ PrintSizeCtlr│  │ PrintSizeSvc │  │              │  │ PrintSize    │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │
│                                    │                                    │
│                                    ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    SECURITY LAYER                                │   │
│  │  JWT Token Filter → Authentication Manager → Role Checker       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
          ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
          │  SUPABASE    │  │  TOYYIBPAY  │  │  SUPABASE   │
          │  PostgreSQL  │  │   Payment   │  │   Storage   │
          │   (DB)      │  │   Gateway   │  │  (Images)   │
          └─────────────┘  └─────────────┘  └─────────────┘
```

---

## 4. Database Schema

### 4.1 Entity Relationship Diagram

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│      USER       │       │     ORDER       │       │   ORDER_ITEM    │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id (UUID) PK   │◄──┐   │ id (UUID) PK    │◄──────│ id (UUID) PK   │
│ email (unique) │   │   │ order_number    │       │ order_id (FK)  │
│ password_hash  │   │   │ user_id (FK)    │       │ size_id         │
│ name           │   │   │ affiliate_id(FK)│       │ quantity        │
│ phone          │   │   │ customer_name   │       │ unit_price      │
│ avatar_url     │   │   │ customer_email  │       │ total_price     │
│ role           │   │   │ customer_phone  │       │ images (JSONB)  │
│ affiliate_code │   │   │ customer_state  │       │ custom_texts    │
│ referred_by    │   │   │ status          │       │ s3_keys (JSONB) │
│ created_at     │   │   │ payment_status  │       │ created_at      │
│ updated_at     │   │   │ payment_method  │       └─────────────────┘
└─────────────────┘   │   │ toyyibpay_ref  │
                       │   │ subtotal       │
┌─────────────────┐   │   │ shipping       │
│  USER (referral)│   │   │ total          │
├─────────────────┤   │   │ paid_at        │
│ id (UUID) PK   │───┘   │ tracking_num   │
│ affiliate_code  │       │ shipped_at     │
└─────────────────┘       │ delivered_at   │
                           │ notes          │
┌─────────────────┐       │ expires_at     │── NEW: DRAFT TTL
│    ADDRESS      │       │ draft_expired_at│── NEW: when marked EXPIRED
├─────────────────┤       │ created_at     │
│ id (UUID) PK   │       │ updated_at     │
│ user_id (FK)   │◄──────└───────┬──────────┘
│ label          │               │
│ name           │               │ 1:N
│ phone          │               ▼
│ house_unit_no  │       ┌─────────────────┐
│ address_line_1 │       │STATUS_HISTORY   │
│ address_line_2 │       ├─────────────────┤
│ city           │       │ id (UUID) PK   │
│ state          │       │ order_id (FK)  │
│ postal_code    │       │ status         │
│ country        │       │ message        │
│ is_default     │       │ created_at     │
│ created_at     │       └─────────────────┘
└─────────────────┘
```

### 4.2 SQL Schema (PostgreSQL)

```sql
-- =============================================
-- USERS TABLE
-- =============================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    phone VARCHAR(50),
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    affiliate_code VARCHAR(50) UNIQUE,
    referred_by UUID REFERENCES users(id),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_affiliate_code ON users(affiliate_code);
CREATE INDEX idx_users_role ON users(role);

-- =============================================
-- ORDERS TABLE
-- =============================================
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    affiliate_id UUID REFERENCES users(id) ON DELETE SET NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(50),
    customer_state VARCHAR(10) DEFAULT 'W',
    
    -- Status: DRAFT, PENDING, PROCESSING, POSTED, ON_DELIVERY, DELIVERED, CANCELLED, REFUNDED, EXPIRED
    status VARCHAR(20) DEFAULT 'PENDING',
    CONSTRAINT orders_status_check CHECK (status IN ('DRAFT','PENDING','PROCESSING','POSTED','ON_DELIVERY','DELIVERED','CANCELLED','REFUNDED','EXPIRED')),
    
    -- Payment: PENDING, PAID, FAILED
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    payment_method VARCHAR(20), -- BANK_TRANSFER, TOYYIBPAY
    toyyibpay_ref VARCHAR(100),
    
    subtotal DECIMAL(10,2) NOT NULL,
    shipping DECIMAL(10,2) DEFAULT 0,
    total DECIMAL(10,2) NOT NULL,
    
    paid_at TIMESTAMP WITH TIME ZONE,
    tracking_number VARCHAR(100),
    shipped_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancel_reason TEXT,
    notes TEXT,
    
    expires_at TIMESTAMP WITH TIME ZONE,       -- When DRAFT expires (set 24h after creation)
    draft_expired_at TIMESTAMP WITH TIME ZONE,  -- When DRAFT was actually marked EXPIRED
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =============================================
-- ORDER ITEMS TABLE
-- =============================================
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    size_id VARCHAR(10) NOT NULL,
    size_name VARCHAR(20) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    images JSONB NOT NULL DEFAULT '[]',
    custom_texts JSONB DEFAULT '[]',
    s3_keys JSONB DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- =============================================
-- ORDER STATUS HISTORY TABLE
-- =============================================
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT order_status_history_status_check CHECK (status IN ('DRAFT','PENDING','PROCESSING','POSTED','ON_DELIVERY','DELIVERED','CANCELLED','REFUNDED','EXPIRED')),
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);

-- =============================================
-- PRINT SIZES TABLE (Reference Data)
-- =============================================
CREATE TABLE print_sizes (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    width DECIMAL(5,2) NOT NULL,
    height DECIMAL(5,2) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Addresses table (saved user addresses)
CREATE TABLE addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(20) NOT NULL DEFAULT 'Other',
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    house_unit_no VARCHAR(50),
    address_line_1 VARCHAR(200) NOT NULL,
    address_line_2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'Malaysia',
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE INDEX idx_addresses_user_id ON addresses(user_id);

-- Default print sizes
INSERT INTO print_sizes (id, name, display_name, width, height, price, description) VALUES
('2R', '2R', '2R (2.5 x 3.5 inches)', 2.5, 3.5, 3.00, 'Wallet size - Perfect for keepsakes'),
('3R', '3R', '3R (3.5 x 5 inches)', 3.5, 5.0, 4.00, 'Standard photo size - Great for albums'),
('4R', '4R', '4R (4 x 6 inches)', 4.0, 6.0, 5.00, 'Most popular - Classic polaroid style'),
('A4', 'A4', 'A4 (8.3 x 11.7 inches)', 8.3, 11.7, 15.00, 'Poster size - Perfect for displays');
```

---

## 5. API Endpoints

### 5.1 Authentication

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/auth/register` | Register new customer | Public |
| POST | `/api/auth/login` | Login, returns JWT | Public |
| POST | `/api/auth/refresh` | Refresh JWT token | Public (with refreshToken) |
| GET | `/api/auth/me` | Get current user | Auth |
| GET | `/api/auth/profile` | Get user profile with order/draft/address counts | Auth |
| POST | `/api/auth/google` | Google OAuth callback (creates/finds user, returns JWT) | Public |
| POST | `/api/auth/setup-admin` | Create admin (secret-protected) | Public |

### 5.2 Orders (Public)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/orders` | Create new order (DRAFT for guests, PENDING for authed) | Optional |
| GET | `/api/orders?orderNumber=X` | Get order by number | Public |
| GET | `/api/orders/{orderNumber}` | Get order by number path param | Public |
| GET | `/api/orders/my?page=0&size=20&status=DRAFT` | Get my paginated orders (with status filter) | Auth |
| POST | `/api/orders/{orderNumber}/pay` | Initiate ToyyibPay payment | Auth |
| POST | `/api/orders/{orderNumber}/mock-pay` | Mock payment (dev only) | Auth |
| PUT | `/api/orders` | Update order (items, address) | Auth |
| DELETE | `/api/orders?orderId=X` | Cancel order | Auth |
| GET | `/api/orders/payment-return` | ToyyibPay return redirect handler | Public |

### 5.3 Addresses

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/addresses` | List my addresses (default first) | Auth |
| POST | `/api/addresses` | Create address (max 10) | Auth |
| PUT | `/api/addresses/{id}` | Update address | Auth |
| DELETE | `/api/addresses/{id}` | Delete address | Auth |
| PATCH | `/api/addresses/{id}/default` | Set as default address | Auth |

### 5.4 Print Sizes (Public)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/print-sizes` | Get active print sizes (prices from DB) | Public |

### 5.5 Orders (Admin/Packer/Marketing)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/admin/orders` | List orders (paginated, filtered) | Packer+ |
| GET | `/api/admin/orders/{id}` | Get order details | Packer+ |
| PATCH | `/api/admin/orders/{id}/status` | Update status | Marketing+ |
| PATCH | `/api/admin/orders/{id}/tracking` | Add tracking # | Packer+ |
| POST | `/api/admin/orders/{id}/notes` | Add internal notes | Marketing+ |

### 5.6 Stats & Analytics

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/admin/stats/overview` | Dashboard stats | Marketing+ |
| GET | `/api/admin/stats/sales` | Sales data | Marketing+ (no revenue for Marketing) |
| GET | `/api/admin/stats/orders-by-status` | Orders count by status | Marketing+ |
| GET | `/api/admin/stats/top-sizes` | Best selling sizes | Marketing+ |
| GET | `/api/admin/stats/by-state` | Orders by state | Admin only |

### 5.7 System Info (Super Admin Only)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/admin/system/storage` | Storage usage (S3/Supabase) | Super Admin |
| GET | `/api/admin/system/database` | Database size & health | Super Admin |
| GET | `/api/admin/system/payment-costs` | ToyyibPay transaction fees | Super Admin |
| GET | `/api/admin/system/server` | Server health & metrics | Super Admin |

### 5.8 File Management

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/upload` | Upload image to Supabase | Auth |
| GET | `/api/orders/{id}/download` | Download all images as ZIP | Packer+ |
| DELETE | `/api/files/{key}` | Delete image | Auth |

### 5.9 ToyyibPay Webhook

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/webhooks/toyyibpay` | Payment callback | ToyyibPay |

### 5.10 User Management (Admin)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/admin/users` | List users | Admin |
| GET | `/api/admin/users/{id}` | Get user details | Admin |
| PATCH | `/api/admin/users/{id}/role` | Update user role | Admin |
| POST | `/api/admin/affiliates/generate-code` | Generate affiliate code | Admin |

---

## 6. User Roles & Permissions

### 6.1 Role Enum
```java
public enum Role {
    CUSTOMER,    // Regular customer - place orders, view own orders
    AFFILIATE,  // Marketing affiliate - view referred orders only
    PACKER,     // Warehouse staff - update to POSTED/DELIVERED only
    MARKETING,  // Marketing team - view all orders, update status
    ADMIN       // Super Admin - full system access including billing, storage, server
}
```

### 6.2 Permission Matrix

| Feature | Customer | Affiliate | Packer | Marketing | Admin |
|---------|:--------:|:---------:|:------:|:---------:|:-----:|
| **Auth** | | | | | |
| Register/Login | ✅ | ✅ | ✅ | ✅ | ✅ |
| View Profile | Own | Own | Own | Own | ✅ |
| **Orders** | | | | | |
| Place Order | ✅ | ✅ | ✅ | ✅ | ✅ |
| View Own Orders | ✅ | Own | ❌ | ✅ | ✅ |
| View All Orders | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Order Management** | | | | | |
| Update Status | ❌ | ❌ | 🚫 P/D only | ✅ | ✅ |
| Add Tracking | ❌ | ❌ | ✅ | ✅ | ✅ |
| Cancel Order | Own | Own | ❌ | ✅ | ✅ |
| Add Notes | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Files** | | | | | |
| Upload Images | ✅ | ✅ | ✅ | ✅ | ✅ |
| Download Order Images | ❌ | ❌ | ✅ | ✅ | ✅ |
| Delete Images | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Analytics** | | | | | |
| View Dashboard | ❌ | ❌ | ❌ | 🚫 No Rev | ✅ |
| View Sales Stats | ❌ | ❌ | ❌ | ❌ | ✅ |
| View by State | ❌ | ❌ | ❌ | ❌ | ✅ |
| **System (Super Admin)** | | | | | |
| Storage Usage | ❌ | ❌ | ❌ | ❌ | ✅ |
| Database Health | ❌ | ❌ | ❌ | ❌ | ✅ |
| Payment Costs | ❌ | ❌ | ❌ | ❌ | ✅ |
| Server Metrics | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Users** | | | | | |
| Manage Users | ❌ | ❌ | ❌ | ❌ | ✅ |
| Change Roles | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Settings** | | | | | |
| Site Settings | ❌ | ❌ | ❌ | ❌ | ✅ |
| Print Sizes | ❌ | ❌ | ❌ | ❌ | ✅ |

*🚫 P/D only = Posted/Delivered only  
🚫 No Rev = Can see orders but not revenue*

---

## 7. Security & Authentication

### 7.1 JWT Configuration

```java
// JWT Properties (application.yml)
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-here-min-32-chars}
  expiration: 86400000  // 24 hours in milliseconds
  refresh-expiration: 604800000  // 7 days
```

### 7.2 Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/setup-admin").permitAll()
                .requestMatchers("/api/orders/{orderNumber}").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers("/api/auth/google").permitAll()
                .requestMatchers("/api/files/upload").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()

                // Admin endpoints
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MARKETING", "PACKER")

                // Super Admin endpoints
                .requestMatchers("/api/admin/system/**").hasRole("ADMIN")

                // Authenticated endpoints
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### 7.3 Role-Based Access Control

```java
// Example: Admin only
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public ResponseEntity<List<User>> getAllUsers() { ... }

// Example: Marketing and above
@PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
@GetMapping("/admin/stats/overview")
public ResponseEntity<StatsOverview> getStats() { ... }

// Example: Packer can only update to POSTED or DELIVERED
@PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
@PatchMapping("/admin/orders/{id}/status")
public ResponseEntity<Order> updateStatus(
    @PathVariable UUID id,
    @Valid @RequestBody OrderStatusUpdateDto dto
) {
    // Validate packer can only set specific statuses
    if (currentUser.getRole() == Role.PACKER) {
        if (!List.of("POSTED", "DELIVERED").contains(dto.getStatus())) {
            throw new AccessDeniedException("Packer can only update to POSTED or DELIVERED");
        }
    }
    // ... proceed
}

// Example: Super Admin system endpoints
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/system/storage")
public ResponseEntity<StorageInfo> getStorageUsage() { ... }
```

---

## 8. File Storage (Supabase)

### 8.1 Bucket Structure

```
polaroid-glossy-bucket/
├── original/
│   └── {orderId}/
│       └── {uuid}.jpg
├── processed/
│   └── {orderId}/
│       └── {uuid}_{size}.jpg
└── thumbnails/
    └── {orderId}/
        └── {uuid}_thumb.jpg
```

### 8.2 Storage Service (Super Admin Can View)

```java
@Service
public class StorageService {
    
    public StorageInfo getStorageUsage() {
        // Get bucket info from Supabase
        // Return: total size, file count, bandwidth used
        
        return StorageInfo.builder()
            .totalSizeGB(0.5)
            .usedSizeGB(0.15)
            .fileCount(234)
            .bandwidthGB(2.5)
            .build();
    }
}
```

---

## 9. Payment Integration (ToyyibPay)

### 9.1 Create Bill

```java
@Service
public class PaymentService {
    
    private final String toyyibpaySecretKey;
    private final String categoryCode;
    private final String returnUrl;
    private final String callbackUrl;
    
    public PaymentResponse createBill(Order order) {
        // Create bill with ToyyibPay API
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userSecretKey", toyyibpaySecretKey);
        params.add("categoryCode", categoryCode);
        params.add("billName", order.getOrderNumber().substring(0, 30));
        params.add("billDescription", "Polaroid Glossy - " + order.getOrderNumber());
        params.add("billPriceSetting", "1");
        params.add("billPayorInfo", "1");
        params.add("billAmount", String.valueOf(order.getTotal().multiply(new BigDecimal("100")).intValue()));
        params.add("billReturnUrl", returnUrl + "?order_id=" + order.getOrderNumber());
        params.add("billCallbackUrl", callbackUrl);
        params.add("billExternalReferenceNo", order.getOrderNumber());
        params.add("billTo", order.getCustomerName());
        params.add("billEmail", order.getCustomerEmail());
        params.add("billPhone", order.getCustomerPhone() != null ? order.getCustomerPhone() : "");
        params.add("billPaymentChannel", "0");
        params.add("billChargeToCustomer", "1");
        
        // Call ToyyibPay API
        // Returns BillCode
        
        // Update order with toyyibpay reference
        // Return payment URL: https://toyyibpay.com/{billCode}
    }
}
```

### 9.2 Payment Cost Tracking (Super Admin)

```java
@Service
public class PaymentCostService {
    
    public PaymentCostInfo getPaymentCosts(LocalDate from, LocalDate to) {
        // Calculate ToyyibPay fees based on transactions
        // 2.5% per transaction (example rate)
        
        List<Order> paidOrders = orderRepository.findByPaymentStatusAndPaidAtBetween(
            PaymentStatus.PAID, from, to
        );
        
        BigDecimal totalAmount = paidOrders.stream()
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal feeRate = new BigDecimal("0.025"); // 2.5%
        BigDecimal totalFee = totalAmount.multiply(feeRate);
        
        return PaymentCostInfo.builder()
            .periodFrom(from)
            .periodTo(to)
            .totalTransactions(paidOrders.size())
            .totalAmount(totalAmount)
            .feeRate(feeRate)
            .totalFee(totalFee)
            .netAmount(totalAmount.subtract(totalFee))
            .build();
    }
}
```

---

## 10. Project Structure

```
polaroid-backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/polaroid/
│   │   │   ├── PolaroidApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── JwtConfig.java
│   │   │   │   ├── SupabaseConfig.java
│   │   │   │   └── SwaggerConfig.java
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── OrderController.java
│   │   │   │   ├── AdminController.java
│   │   │   │   ├── AddressController.java
│   │   │   │   ├── FileController.java
│   │   │   │   ├── PrintSizeController.java
│   │   │   │   ├── SystemController.java
│   │   │   │   └── WebhookController.java
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── OrderService.java
│   │   │   │   ├── AddressService.java
│   │   │   │   ├── PaymentService.java
│   │   │   │   ├── FileService.java
│   │   │   │   ├── StatsService.java
│   │   │   │   ├── PrintSizeService.java
│   │   │   │   ├── DraftCleanupService.java
│   │   │   │   └── SystemService.java
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── OrderRepository.java
│   │   │   │   ├── OrderItemRepository.java
│   │   │   │   ├── OrderStatusHistoryRepository.java
│   │   │   │   ├── AddressRepository.java
│   │   │   │   └── PrintSizeRepository.java
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── base/
│   │   │   │   │   └── Auditable.java
│   │   │   │   ├── User.java
│   │   │   │   ├── Order.java
│   │   │   │   ├── OrderItem.java
│   │   │   │   ├── OrderStatusHistory.java
│   │   │   │   ├── Address.java
│   │   │   │   ├── PrintSize.java
│   │   │   │   └── enums/
│   │   │   │       ├── Role.java
│   │   │   │       ├── OrderStatus.java (DRAFT, PENDING, ..., EXPIRED)
│   │   │   │       └── PaymentStatus.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── OrderRequest.java
│   │   │   │   │   ├── OrderStatusUpdateRequest.java
│   │   │   │   │   ├── UpdateOrderRequest.java
│   │   │   │   │   ├── UpdatePaymentStatusRequest.java
│   │   │   │   │   ├── AddressRequest.java
│   │   │   │   │   ├── PrintSizeRequest.java
│   │   │   │   │   └── UserRoleUpdateRequest.java
│   │   │   │   │
│   │   │   │   ├── response/
│   │   │   │   │   ├── AuthResponse.java
│   │   │   │   │   ├── UserResponse.java
│   │   │   │   │   ├── OrderResponse.java
│   │   │   │   │   ├── OrderItemResponse.java
│   │   │   │   │   ├── StatusHistoryResponse.java
│   │   │   │   │   ├── AddressResponse.java
│   │   │   │   │   ├── PrintSizeResponse.java
│   │   │   │   │   ├── StatsOverviewResponse.java
│   │   │   │   │   └── SystemInfoResponse.java
│   │   │   │   │
│   │   │   │   └── mapper/
│   │   │   │       └── OrderMapper.java
│   │   │   │
│   │   │   ├── security/
│   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   └── CustomUserDetailsService.java
│   │   │   │
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       ├── ResourceNotFoundException.java
│   │   │       └── AccessDeniedException.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   │
│   └── test/
│       └── java/com/polaroid/
│           ├── controller/
│           ├── service/
│           └── repository/
│
└── README.md
```

---

## 11. Configuration

### 11.1 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: polaroid-backend
  
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/polaroid}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

# JWT Configuration
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-minimum-32-characters-long}
  expiration: 86400000
  refresh-expiration: 604800000

# Supabase Configuration
supabase:
  url: ${SUPABASE_URL:https://your-project.supabase.co}
  key: ${SUPABASE_KEY:your-anon-key}
  storage-bucket: polaroid-glossy

# ToyyibPay Configuration
toyyibpay:
  secret-key: ${TOYYIBPAY_SECRET_KEY:your-secret-key}
  category-code: ${TOYYIBPAY_CATEGORY_CODE:your-category}
  return-url: ${TOYYIBPAY_RETURN_URL:http://localhost:3000/payment-status}
  callback-url: ${TOYYIBPAY_CALLBACK_URL:http://localhost:8080/api/webhooks/toyyibpay}
  fee-percentage: 2.5

# CORS
cors:
  allowed-origins: ${CORS_ORIGINS:http://localhost:3000}

# Logging
logging:
  level:
    com.polaroid: DEBUG
    org.springframework.security: DEBUG
```

### 11.2 Environment Variables

```bash
# Database
DATABASE_URL=jdbc:postgresql://your-supabase-url.supabase.co:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=your-db-password

# JWT
JWT_SECRET=your-super-secret-jwt-key-min-32-chars

# Supabase
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key

# ToyyibPay
TOYYIBPAY_SECRET_KEY=your-secret-key
TOYYIBPAY_CATEGORY_CODE=your-category-code
TOYYIBPAY_RETURN_URL=http://localhost:3000/payment-status
TOYYIBPAY_CALLBACK_URL=http://localhost:8080/api/webhooks/toyyibpay

# CORS
CORS_ORIGINS=http://localhost:3000
```

---

## 12. Implementation Phases

### Phase 1: Project Setup ✅
- [x] Create Spring Boot project with Maven
- [x] Configure pom.xml with dependencies
- [x] Set up application.yml configuration
- [x] Configure CORS for Next.js frontend
- [x] Set up PostgreSQL connection (local Docker / Supabase)
- [x] Create base project structure

### Phase 2: Authentication ✅
- [x] Implement User entity and repository
- [x] Configure Spring Security
- [x] Implement JWT token provider
- [x] Create login/register endpoints
- [x] Add role-based access control
- [x] Test authentication flow

### Phase 3: Database Models ✅
- [x] Create Order, OrderItem, StatusHistory entities
- [x] Set up JPA repositories
- [x] Create print sizes reference data
- [x] Add database indexes for performance

### Phase 4: Order Management ✅
- [x] Create order endpoints (public)
- [x] Implement order creation with items
- [x] Add order tracking endpoint
- [x] Implement ToyyibPay bill creation
- [x] Implement webhook callback handler
- [x] Add order status updates

### Phase 5: Admin Features ✅
- [x] Create admin endpoints
- [x] Implement order filtering/pagination
- [x] Build stats/analytics endpoints
- [x] Add status update with validation
- [x] Add tracking number management

### Phase 6: File Storage ✅
- [x] Integrate Supabase Storage
- [x] Implement image upload
- [x] Create ZIP download for orders
- [x] Add image deletion

### Phase 7: Super Admin System Features ✅
- [x] Storage usage endpoint
- [x] Database health endpoint
- [x] Payment cost tracking
- [x] Server metrics endpoint

### Phase 8: Frontend Integration ✅
- [x] Next.js frontend calling Spring Boot backend
- [x] Environment variables configured (NEXT_PUBLIC_API_URL)
- [x] End-to-end login/auth flow working
- [x] AuthContext fixed to fetch user profile via /auth/me after login

### Phase 9: Guest Draft Orders & Address Book ✅
- [x] Add DRAFT, EXPIRED to OrderStatus enum
- [x] Add expires_at, draft_expired_at to orders table (Flyway V8)
- [x] Create Address entity with full CRUD (Flyway V8)
- [x] Create AddressService (max 10/user, single default flag)
- [x] Create AddressController (GET/POST/PUT/DELETE + PATCH default)
- [x] Guest orders create as DRAFT with 24h expiry
- [x] Payment webhook transitions DRAFT→PENDING
- [x] FileService allows DRAFT uploads, blocks EXPIRED
- [x] DraftCleanupService (cron every 10 min)
- [x] Paginated `/api/orders/my` with status filter
- [x] `/api/auth/profile` endpoint with counts
- [x] Update orders & order_status_history CHECK constraints (V10, V11)

### Phase 10: Testing & Deployment
- [x] Deploy to Fly.io production
- [x] Tests passing (15/15)
- [ ] Write more integration tests
- [ ] Configure CI/CD

---

## Summary

This architecture document provides a complete blueprint for building the Polaroid Glossy backend with:

- ✅ **5 User Roles** with granular permissions
- ✅ **Supabase** for database and storage
- ✅ **JWT Authentication** with Spring Security
- ✅ **ToyyibPay** payment integration
- ✅ **Admin features** (stats, order management)
- ✅ **Super Admin features** (storage, database health, payment costs, server metrics)
- ✅ **Role-based access control** at every level

---

## Quick Reference - Role Summary

| Role | Description |
|------|-------------|
| **CUSTOMER** | Place orders, view own orders |
| **AFFILIATE** | View only their referred orders |
| **PACKER** | Update status to POSTED/DELIVERED, download images |
| **MARKETING** | View all orders, update status, download images (no financials) |
| **ADMIN** | Full access including system health, storage, payment costs |
