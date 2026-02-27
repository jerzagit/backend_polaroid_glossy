# Quick Start Guide

This guide will help you get the Polaroid Glossy backend running in minutes.

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| Java | 17+ | https://adoptium.net/ |
| Maven | 3.9+ | Included in project |
| Docker | Latest | https://www.docker.com/products/docker-desktop/ |

---

## Step 1: Start PostgreSQL (Using Docker)

Run this command in PowerShell or Command Prompt:
```bash
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password -e POSTGRES_DB=polaroid --name polaroid-postgres postgres:15
```

Verify PostgreSQL is running:
```bash
docker ps
```

You should see `polaroid-postgres` in the list.

To stop/restart:
```bash
docker stop polaroid-postgres
docker start polaroid-postgres
```

---

## Step 2: Configure Database

Edit `src/main/resources/application-dev.yml`:

```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/polaroid
  driver-class-name: org.postgresql.Driver
  username: postgres
  password: password  # Change to your password

flyway:
  enabled: false  # Flyway is disabled in dev; Hibernate manages the schema via ddl-auto: update
```

---

## Step 3: Start the Backend

```bash
./apache-maven-3.9.6/bin/mvn.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=dev"
```

Wait for this message:
```
Started PolaroidApplication in X.XXX seconds
```

The API is now available at `http://localhost:8080`

---

## Step 4: Create Admin User

### Option A: Using API
```bash
curl -X POST "http://localhost:8080/api/auth/setup-admin?secret=admin-secret-2024" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@polaroid.com",
    "password": "admin123",
    "name": "Admin User",
    "phone": "+60123456789"
  }'
```

### Option B: Database (after migrations run)
Register a new user via API, then update role to ADMIN in database:
```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
```

---

## Step 5: Login

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "superadmin@polaroid.com",
    "password": "admin123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "email": "superadmin@polaroid.com",
  "name": "Super Admin",
  "role": "ADMIN",
  "expiresIn": 86400000
}
```

---

## API Documentation

### Authentication Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new customer |
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/setup-admin?secret=xxx` | Create admin (first time only) |
| GET | `/api/auth/me` | Get current user |

### Admin Endpoints (require ADMIN role)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | List users |
| GET | `/api/admin/orders` | List orders |
| PATCH | `/api/admin/orders/{id}/status` | Update order status |
| GET | `/api/admin/stats/overview` | Dashboard stats |

---

## Troubleshooting

### "Connection refused" error
- PostgreSQL is not running → Start PostgreSQL service
- Wrong password → Check `application-dev.yml` credentials

### "Database does not exist" error
- Run: `CREATE DATABASE polaroid;` in PostgreSQL

### "Port 8080 already in use"
- Change port in `application-dev.yml`:
  ```yaml
  server:
    port: 8081
  ```

### Flyway migration errors
- Disable Flyway temporarily:
  ```yaml
  flyway:
    enabled: false
  ```
- Manually run SQL migrations in `src/main/resources/db/migration/`

---

## Next Steps

1. **Start Frontend** - See `frontend/README.md`
2. **Configure Supabase** - For file storage (optional for dev)
3. **Configure ToyyibPay** - For payment processing (optional for dev)

---

## Default Test Accounts

| Email | Password | Role |
|-------|----------|------|
| superadmin@polaroid.com | admin123 | ADMIN |
| test2@test.com | test123 | CUSTOMER |

> **Note:** The seed users in `V2__Seed_data.sql` (john.doe@example.com etc.) have broken BCrypt hashes and cannot be used for login. Use the accounts above.

To create a new admin:
```bash
curl -X POST "http://localhost:8080/api/auth/setup-admin?secret=admin-secret-2024" \
  -H "Content-Type: application/json" \
  -d '{"email":"newadmin@example.com","password":"yourpassword","name":"New Admin","phone":"+60123456789"}'
```
