# Product Catalog API — Implementation Plan

> **Branch:** `feat/product-catalog-api`
> **Goal:** Add the missing ProductMeta model, pricing tiers, and merged `/api/products` endpoints that the Next.js frontend expects.

---

## Current State (Already Exists)

| Component | File | Status |
|-----------|------|--------|
| `PrintSize` entity | `model/PrintSize.java` | Done — has id, name, displayName, width, height, price, description, isActive, tag |
| `PrintSizeRepository` | `repository/PrintSizeRepository.java` | Done — `findByIsActiveTrue()` |
| `PrintSizeService` | `service/PrintSizeService.java` | Done — CRUD + `getActivePrintSizes()` |
| `PrintSizeController` | `controller/PrintSizeController.java` | Done — `GET /api/print-sizes` |
| `PrintSizeResponse` | `dto/response/PrintSizeResponse.java` | Done |
| `PrintSizeRequest` | `dto/request/PrintSizeRequest.java` | Done |
| `PrintSizeMapper` | `dto/mapper/PrintSizeMapper.java` | Done |
| Flyway migrations | `db/migration/V1..V17` | Done |
| Seed data | `V1` seeds 2R/3R/4R/A4; `V16` deactivates 2R/3R | Done |

## What's Missing (What We're Building)

| # | Component | Purpose |
|---|-----------|---------|
| 1 | `ProductMeta` entity | Marketing data: images, features, accentColor, shortDesc, fullDesc, rating, reviewCount, popular, tiktokVideos |
| 2 | `PrintSizePricingTier` entity | Bulk pricing tiers per product |
| 3 | `ProductMetaRepository` | JPA repository for ProductMeta |
| 4 | `PrintSizePricingTierRepository` | JPA repository for pricing tiers |
| 5 | `GET /api/products` endpoint | Merged view: print size + meta + pricing tiers → `ProductListingResponse` |
| 6 | `GET /api/products/{id}` endpoint | Single product detail (same shape as list item) |
| 7 | `ProductListingResponse` DTO | The merged response the frontend expects |
| 8 | Flyway migration `V18` | Create `product_meta` + `print_size_pricing_tiers` tables |
| 9 | Flyway migration `V19` | Seed product_meta from products-meta.json data |
| 10 | Flyway migration `V20` | Seed pricing tiers from products-meta.json data |
| 11 | Flyway migration `V21` | Add missing print sizes (ic-border, ic, polaroid-mini, strip-3, strip-4) |

---

## Implementation Steps

### Step 1: Create `ProductMeta` entity

**File:** `src/main/java/com/polaroid/model/ProductMeta.java`

Fields:
- `id` (String, PK) — matches `print_sizes.id`
- `tag` (String)
- `accentColor` (String)
- `images` (String, JSON array — stored as TEXT)
- `features` (String, JSON array — stored as TEXT)
- `tiktokVideos` (String, JSON array — stored as TEXT)
- `shortDescription` (String)
- `fullDescription` (String, TEXT)
- `rating` (Double)
- `reviewCount` (Integer)
- `popular` (Boolean)

### Step 2: Create `PrintSizePricingTier` entity

**File:** `src/main/java/com/polaroid/model/PrintSizePricingTier.java`

Fields:
- `id` (String, PK)
- `sizeId` (String, FK → print_sizes.id)
- `quantity` (Integer)
- `regularPrice` (BigDecimal)
- `discountedPrice` (BigDecimal)
- `sortOrder` (Integer)

### Step 3: Create repositories

**File:** `src/main/java/com/polaroid/repository/ProductMetaRepository.java`
- `findById(String)` — Optional<ProductMeta>
- `findAll()` — List<ProductMeta>

**File:** `src/main/java/com/polaroid/repository/PrintSizePricingTierRepository.java`
- `findBySizeIdOrderByQuantityAsc(String)` — List<PrintSizePricingTier>

### Step 4: Create `ProductListingResponse` DTO

**File:** `src/main/java/com/polaroid/dto/response/ProductListingResponse.java`

Fields (matching the frontend `ProductListing` interface):
- `id`, `name`, `displayName`, `width`, `height`, `price`, `description`
- `shortDescription`, `fullDescription`
- `images` (List<String>), `image` (String)
- `popular` (Boolean), `tag` (String)
- `features` (List<String>), `accentColor` (String)
- `specs` (Map<String, String>)
- `rating` (Double), `reviewCount` (Integer)
- `pricingTiers` (List<PricingTierResponse>)
- `tiktokVideos` (List<TikTokVideoResponse>)
- `available` (Boolean)

### Step 5: Create nested DTOs

**File:** `src/main/java/com/polaroid/dto/response/PricingTierResponse.java`
- `quantity`, `regularPrice`, `discountedPrice`

**File:** `src/main/java/com/polaroid/dto/response/TikTokVideoResponse.java`
- `videoId`, `url`, `caption`

### Step 6: Create `ProductService` with merge logic

**File:** `src/main/java/com/polaroid/service/ProductService.java`

- `getAllProducts()` — fetches all active print sizes, loads all meta + tiers, merges into `List<ProductListingResponse>`
- `getProductById(String id)` — same but for single product

Merge logic:
1. Get all active print sizes
2. Load all ProductMeta into Map<id, meta>
3. Load all PrintSizePricingTier into Map<sizeId, List<tier>>
4. For each print size, build ProductListingResponse:
   - From PrintSize: id, name, displayName, width, height, price, description, available=true
   - From ProductMeta: images, popular, tag, features, accentColor, rating, reviewCount, shortDescription, fullDescription
   - Build specs map: dimensions, paper, finish, printMethod, processingTime, minQty
   - From PricingTiers: pricingTiers list
   - tiktokVideos from meta
   - image = images[0] or null

### Step 7: Create `ProductController`

**File:** `src/main/java/com/polaroid/controller/ProductController.java`

- `GET /api/products` — returns all merged products
- `GET /api/products/{id}` — returns single product

### Step 8: Flyway migrations

**V18__Create_product_meta_and_pricing_tiers.sql:**
- Create `product_meta` table
- Create `print_size_pricing_tiers` table
- Add indexes

**V19__Seed_product_meta.sql:**
- Insert 12 product_meta records from `products-meta.json`

**V20__Seed_pricing_tiers.sql:**
- Insert all pricing tiers from `products-meta.json`

**V21__Add_missing_print_sizes.sql:**
- Insert missing print sizes: ic-border, ic, polaroid-mini, strip-3, strip-4
- Update existing print sizes with correct prices/dimensions

### Step 9: Build and test

- `./mvnw clean compile`
- `./mvnw test`

---

## Data to Seed (from frontend `products-meta.json`)

### ProductMeta seed data:

| id | tag | accentColor | rating | reviewCount | popular |
|----|-----|-------------|--------|-------------|---------|
| ic-border | POLAROID | #8b5cf6 | 4.9 | 1302 | true |
| ic | POLAROID | #ec4899 | 4.9 | 1302 | true |
| polaroid-mini | MINI | #0ea5e9 | 4.9 | 1302 | true |
| 2r-no-border | 2R FULL | #f97316 | 4.9 | 0 | false |
| 2r-border | 2R BORDER | #f59e0b | 4.8 | 0 | false |
| 3r-no-border | 3R FULL | #ef4444 | 4.9 | 0 | false |
| 3r-border | 3R BORDER | #d946ef | 4.8 | 0 | false |
| 4r | BESTSELLER | #eab308 | 4.9 | 1204 | true |
| 5r | 5R | #6366f1 | 4.9 | 0 | false |
| 6r | 6R | #14b8a6 | 4.9 | 0 | false |
| strip-3 | STRIP | #0891b2 | 4.9 | 0 | false |
| strip-4 | STRIP | #7c3aed | 4.9 | 0 | false |

### Print sizes to add/update:

| id | name | displayName | width | height | price | tag |
|----|------|-------------|-------|--------|-------|-----|
| ic-border | IC Border | IC Border (5.5 × 8.9 cm) | 5.5 | 8.9 | 2.50 | POLAROID |
| ic | IC | IC (5.5 × 8.9 cm) | 5.5 | 8.9 | 2.00 | POLAROID |
| polaroid-mini | Polaroid Mini | Polaroid Mini (5.0 × 8.9 cm) | 5.0 | 8.9 | 1.50 | MINI |
| strip-3 | Strip 3 | Strip 3 (3-photo strip) | — | — | 1.80 | STRIP |
| strip-4 | Strip 4 | Strip 4 (4-photo strip) | — | — | 1.80 | STRIP |

Note: 2R and 3R were deactivated in V16. The frontend catalog no longer includes them. We will NOT reactivate them — the frontend uses ic-border/ic/polaroid-mini/4r/5r/6r/strip-3/strip-4 only.

---

## Verification

1. `GET /api/print-sizes` — should return all active print sizes
2. `GET /api/products` — should return merged ProductListingResponse array with all fields populated
3. `GET /api/products/{id}` — should return single product (e.g., `/api/products/ic-border`)
4. Frontend home page should now show products (once `source === 'backend'` gate passes)
