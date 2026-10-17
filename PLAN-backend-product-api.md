# Backend Product API — Requirements for Frontend Parity

> **Status (2026-10-18): implemented and merged.** `GET /api/products` and
> `GET /api/products/{id}` serve the current catalog shape described in
> [`docs/API.md`](docs/API.md#catalog-public) and
> [`docs/catalog/FRONTEND-INTEGRATION.md`](docs/catalog/FRONTEND-INTEGRATION.md). This file
> is kept as the historical requirements document; it is not a to-do list.
>
> **Date:** 2026-10-12
> **Source:** Audit of polaroid_glossy frontend codebase
> **Purpose:** Define what the Spring Boot backend must implement so the existing Next.js frontend can consume product data without changes.

---

## Context

The frontend at `~/Documents/polaroid_glossy_backup` has a complete product catalog UI (card grid, product detail, cart/checkout) that is **currently non-functional** because the Spring Boot backend does not exist yet. The frontend expects specific API endpoints and response shapes. This document defines those requirements.

---

## 1. Database Tables Required

### 1.1 `print_sizes` (Pricing Layer — Admin-owned)

Matches the Prisma `PrintSize` model in the frontend:

| Column | Type | Notes |
|--------|------|-------|
| id | String (PK) | e.g., `ic-border`, `ic`, `2r`, `3r`, `4r`, `5r`, `6r`, `polaroid-mini`, `strip-3`, `strip-4` |
| name | String | e.g., "IC Border", "IC", "2R" |
| displayName | String | e.g., "IC Border (5.5 × 8.9 cm)" |
| width | Float | in cm |
| height | Float | in cm |
| price | Float | base price (single piece) |
| description | String | short description |
| isActive | Boolean | soft delete flag |

### 1.2 `product_meta` (Marketing Layer — Marketing-owned)

Matches the Prisma `ProductMeta` model in the frontend:

| Column | Type | Notes |
|--------|------|-------|
| id | String (PK) | matches print_sizes.id |
| tag | String | e.g., "POLAROID", "MINI", "BESTSELLER" |
| accentColor | String | hex color, e.g., "#8b5cf6" |
| images | JSONB | array of image URL strings |
| features | JSONB | array of feature strings |
| tiktokVideos | JSONB | array of {videoId, url, caption} |
| shortDescription | String | |
| fullDescription | String | |
| rating | Float | e.g., 4.9 |
| reviewCount | Int | e.g., 1302 |
| popular | Boolean | |

### 1.3 `print_size_pricing_tiers` (Bulk Pricing)

The frontend expects tiered pricing per product. This does NOT exist in the current Prisma schema — it only lives in `products-meta.json`. The backend needs a new table:

| Column | Type | Notes |
|--------|------|-------|
| id | String (PK) | |
| sizeId | String (FK) | references print_sizes.id |
| quantity | Int | tier threshold (e.g., 1, 5, 10, 20, 50, 100, 250) |
| regularPrice | Float | |
| discountedPrice | Float | |
| sortOrder | Int | display order |

---

## 2. Current Website Pricing (to seed the backend)

| Product ID | Name | 1 pc | 5 pcs | 10 pcs | 20 pcs | 50 pcs | 100 pcs | 250 pcs |
|------------|------|------|-------|--------|--------|--------|---------|----------|
| `ic-border` | IC Border | RM2.50 | RM7 | RM9 | RM14 | RM30 | RM50 | RM147 |
| `ic` | IC (no border) | RM2.00 | RM6 | RM7 | RM10.50 | RM25 | RM40 | RM88 |
| `polaroid-mini` | Polaroid Mini | RM1.50 | RM4 | RM5 | RM8 | RM15 | RM30 | RM75 |
| `2r-no-border` | 2R No Border | RM2.50 | — | RM7 | RM14 | RM35 | RM60 | — |
| `2r-border` | 2R Border | RM2.00 | — | RM6.50 | RM13 | RM32 | RM60 | — |
| `3r-no-border` | 3R No Border | RM2.50 | — | RM8 | RM16 | RM40 | RM80 | — |
| `3r-border` | 3R Border | RM2.40 | — | RM7 | RM14 | RM35 | RM70 | — |
| `4r` | 4R | RM2.50 | — | RM10 | RM20 | RM50 | RM100 | — |
| `5r` | 5R | RM4.00 | — | RM20 | — | — | — | — |
| `6r` | 6R | RM5.00 | — | RM25 | — | — | — | — |
| `strip-3` | Strip 3 | RM1.80 | — | — | — | — | — | — |
| `strip-4` | Strip 4 | RM1.80 | — | — | — | — | — | — |

---

## 3. API Endpoints Required

### 3.1 `GET /api/print-sizes` (Public — Product Catalog)

The frontend's `product-catalog.ts` calls this endpoint. It must return:

```json
[
  {
    "id": "ic-border",
    "name": "IC Border",
    "displayName": "IC Border (5.5 × 8.9 cm)",
    "width": 5.5,
    "height": 8.9,
    "price": 2.50,
    "description": "Classic white-border Polaroid prints in a 5.5 × 8.9 cm format.",
    "isActive": true
  }
]
```

**Accepts either a bare array or `{ "success": true, "products": [...] }` wrapper.**

Only returns items where `isActive = true`.

### 3.2 `GET /api/products` (Public — Merged Catalog)

The frontend's `/api/products/route.ts` currently does the merge itself, but the backend should eventually provide the merged view:

```json
{
  "success": true,
  "source": "backend",
  "products": [
    {
      "id": "ic-border",
      "name": "IC Border",
      "displayName": "IC Border (5.5 × 8.9 cm)",
      "width": 5.5,
      "height": 8.9,
      "price": 2.50,
      "description": "...",
      "shortDescription": "...",
      "fullDescription": "...",
      "images": ["/images/products/ic-border/ic-border.jpg"],
      "image": "/images/products/ic-border/ic-border.jpg",
      "popular": true,
      "tag": "POLAROID",
      "features": ["White border", "Polaroid style", "Glossy waterproof"],
      "accentColor": "#8b5cf6",
      "specs": {
        "dimensions": "5.5 × 8.9 cm",
        "paper": "Glossy photo-grade 260gsm",
        "finish": "Glossy",
        "printMethod": "Dye-sublimation",
        "processingTime": "3–4 working days",
        "minQty": 1
      },
      "rating": 4.9,
      "reviewCount": 1302,
      "pricingTiers": [
        { "quantity": 1, "regularPrice": 2.5, "discountedPrice": 2.5 },
        { "quantity": 5, "regularPrice": 7, "discountedPrice": 7 },
        { "quantity": 10, "regularPrice": 9, "discountedPrice": 9 }
      ],
      "available": true
    }
  ]
}
```

### 3.3 `GET /api/products/{id}` (Public — Product Detail)

Same shape as the list item above, for a single product. Must include `tiktokVideos`.

### 3.4 Admin Endpoints (from `backend_architecture.md`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/admin/sizes` | GET/POST | List/create print sizes |
| `/api/admin/sizes/{id}` | PUT | Update price/name |
| `/api/admin/sizes/{id}/toggle` | PATCH | Enable/disable |
| `/api/admin/sizes/{id}` | DELETE | Soft delete |
| `/api/admin/products` | GET | Merged list (size + meta) |
| `/api/admin/products/{id}` | GET | Single product detail |
| `/api/admin/products/{id}/meta` | PUT | Update copy, tag, color, features |
| `/api/admin/products/{id}/images` | POST | Upload image to Supabase |
| `/api/admin/products/{id}/images` | DELETE | Remove image |
| `/api/admin/products/{id}/images/reorder` | PUT | Reorder carousel |
| `/api/admin/products/{id}/tiktok` | POST | Add TikTok video |
| `/api/admin/products/{id}/tiktok/{videoId}` | DELETE | Remove TikTok |
| `/api/admin/products/{id}/analytics` | GET | Sales stats |

---

## 4. Key DTOs

### PrintSizeRequest (admin create/update)

```java
public class PrintSizeRequest {
    private String name;
    private String displayName;
    private Float width;
    private Float height;
    private Float price;
    private String description;
    private Boolean isActive;
}
```

### ProductMetaRequest (admin update)

```java
public class ProductMetaRequest {
    private String shortDescription;
    private String fullDescription;
    private String tag;
    private String accentColor;
    private List<String> features;
}
```

### PricingTierRequest

```java
public class PricingTierRequest {
    private String sizeId;
    private Integer quantity;
    private Float regularPrice;
    private Float discountedPrice;
    private Integer sortOrder;
}
```

### ProductAdminResponse (merged — what the frontend consumes)

```java
public class ProductAdminResponse {
    private String id;
    private String name;
    private String displayName;
    private Float width;
    private Float height;
    private Float price;
    private String description;
    private String shortDescription;
    private String fullDescription;
    private List<String> images;
    private String image;
    private Boolean popular;
    private String tag;
    private List<String> features;
    private String accentColor;
    private Map<String, String> specs;
    private Float rating;
    private Integer reviewCount;
    private List<PricingTierRequest> pricingTiers;
    private List<TikTokVideoRequest> tiktokVideos;
    private Boolean available;
}
```

---

## 5. Frontend Consumption Patterns (What the Backend Must Support)

| Frontend File | Fetch | Gate | Notes |
|---------------|-------|------|-------|
| `src/components/ProductCatalog.tsx` | `GET /api/products` | `data.success` | Renders card grid |
| `src/app/page.tsx` (home) | `GET /api/products` | `data.source === 'backend'` | Blocks upload/checkout if not backend |
| `src/app/products/[id]/page.tsx` | `GET /api/products/{id}` | `pData.success` | Product detail with tabs |
| `src/app/api/products/route.ts` | Calls `getCatalog()` | Merges backend sizes + JSON meta | Will eventually call backend directly |
| `src/app/api/products/[id]/route.ts` | Calls `getCatalog()` then finds by ID | Re-attaches `tiktokVideos` | Inefficient — should use `GET /api/products/{id}` |

### Critical: The `source` Field

The home page gates on `data.source === 'backend'`. The backend must return `source: "backend"` in the merged response. If the backend is unreachable, the frontend returns `source: 'unavailable'` and the upload/checkout flow is blocked.

---

## 6. Entity Relationship

```
print_sizes (1) ----< (N) print_size_pricing_tiers
       |
       | (1:1 on id)
       |
product_meta
```

---

## 7. Migration Path

1. Create the three tables in PostgreSQL
2. Seed `print_sizes` from the pricing table in section 2
3. Seed `product_meta` from `~/Documents/polaroid_glossy_backup/src/data/products-meta.json`
4. Seed `print_size_pricing_tiers` from the `pricingTiers` arrays in `products-meta.json`
5. Implement `GET /api/print-sizes` returning active sizes
6. Implement `GET /api/products` merging size + meta + tiers
7. Update frontend `product-catalog.ts` to use the backend merged response instead of doing its own merge

---

## 8. Known Frontend Bugs to Be Aware Of

These are frontend issues that the backend implementation should be aware of (but does not need to fix):

1. **`pricingTiers` not merged in `product-catalog.ts`** — the merge logic doesn't copy `pricingTiers` from JSON meta to `ProductListing`, but the frontend renders them. This means the backend's merged response must include `pricingTiers` or the frontend must be fixed.
2. **Hardcoded star distribution** — product detail reviews tab uses 72%/18%/6%/3%/1% instead of computing from actual review data.
3. **Hardcoded border IDs** — `BORDER_IDS` set is duplicated in `ProductCatalog.tsx` and `page.tsx`.
4. **Double merge** — `products/[id]/route.ts` re-fetches the full catalog just to find one product and re-attach `tiktokVideos`.

---

## 9. Reference Files in Frontend Repo

| File | Purpose |
|------|---------|
| `src/data/products-meta.json` | Source of truth for marketing data (12 products) |
| `src/lib/product-catalog.ts` | Merge logic and `ProductListing` interface |
| `src/lib/backend.ts` | HTTP proxy with 15s timeout and fallback |
| `src/app/api/products/route.ts` | GET /api/products |
| `src/app/api/products/[id]/route.ts` | GET /api/products/:id |
| `prisma/schema.prisma` | PrintSize, Product, ProductMeta models |
| `backend_architecture.md` | Full planned Spring Boot design (1236 lines) |
| `PRICING_CATALOG.md` | TikTok Shop pricing source |
