# Implementation plan: variation cover images (as built)

Companion to `backend-varian-image-cover` in this folder. Scope chosen: a style's cover is a
pointer to one of the product's existing gallery images — no per-variant uploads in this
release.

## Contract

`GET /api/products` and `GET /api/products/{id}` (numeric id, product code, or alias) return
`variants[].coverImageUrl`:

- absolute HTTPS URL when the style has its own cover
- `null` when it does not; the storefront then falls back to the product-level `image`

Product-level `image` / `images`, package data, alias filtering, and existing fields are
unchanged.

## Storage

`product_variants.cover_image_url VARCHAR(2048)` (V29). Nullable, so every existing variant
keeps returning `null` without a backfill.

A cover must be one of the product's `product_meta.images` entries. That is enforced because
the public image endpoint (`GET /api/products/{id}/images/{name}`) only serves URLs still
listed in that gallery — a pointer outside it would render as a broken image.

## Write path

`PUT /api/admin/catalog/variants/{variantId}/cover` (ADMIN only)

    { "coverImageUrl": "https://…" }   # set, must be in this product's gallery
    { "coverImageUrl": null }          # clear, falls back to the product image

Validation: absolute HTTPS with a host and no user info, ≤2048 chars, gallery membership.
A blank string is rejected rather than silently treated as a clear, so a client that
forgets to send a value gets a 400 instead of wiping the style cover.

## Orphan guard

`ProductImageService.updateImages()` clears any variant cover whose URL was removed from the
gallery in the same transaction. Without this a gallery edit would leave styles pointing at a
URL the public endpoint now 404s.

## Code map

| Change | File |
|---|---|
| Column | `src/main/resources/db/migration/V29__Add_variant_cover_image.sql` |
| Entity | `src/main/java/com/polaroid/model/ProductVariant.java` |
| Public DTO | `src/main/java/com/polaroid/dto/response/ProductVariantResponse.java` |
| Mapping (list, detail, alias) | `src/main/java/com/polaroid/service/ProductService.java` — `variantsFor` |
| Admin endpoint | `src/main/java/com/polaroid/controller/CatalogAdminController.java` |
| Admin logic + validation | `src/main/java/com/polaroid/service/CatalogAdminService.java` — `setVariantCover` |
| Gallery + orphan guard | `src/main/java/com/polaroid/service/ProductImageService.java` |
| Request DTO | `src/main/java/com/polaroid/dto/request/admin/CatalogVariantCoverRequest.java` |
| Tests | `src/test/java/com/polaroid/service/VariantCoverImageTest.java`, `CatalogSchemaMappingTest` |

`variantsFor` feeds both list and detail responses, so the two cannot drift; aliases already
route through the same builder with a style filter.

## Seller dashboard

`frontend/src/app/dashboard/catalog/page.tsx` gains a Cover row per style: current cover
thumbnail, a Clear action, and a strip of the product gallery images to pick from. API client
method `catalogAPI.setVariantCover()` in `frontend/src/lib/api.ts`, type in
`frontend/src/types/index.ts`.

## Storefront

`src/lib/product-pricing.ts`: `coverImageUrl` added to `ProductVariant` and preserved by
`normalizeVariants` (it rebuilds each variant field by field, so unlisted fields are dropped).

`src/app/products/[id]/page.tsx`: the gallery shows the selected style's cover first, then the
rest of the product images; with no style cover it uses the product images unchanged.

## Verification

1. Set different covers on IC FULL and BORDER → both appear in `GET /api/products`.
2. `GET /api/products/1`, `/api/products/ic` and `/api/products/ic-border` agree; the alias
   returns only BORDER with its own cover.
3. A style with no cover returns `"coverImageUrl": null`.
4. Change, then clear a cover → the next public response reflects each step (the product
   endpoints are uncached).
5. `curl` the cover URL with no Authorization header → 200 image/png.
6. Remove a gallery image used as a cover → that style's `coverImageUrl` becomes null in the
   same request that changed the gallery.

## Not in scope

Per-variant uploads (a style image that is not in the product gallery). Adding it later needs
either a variant-scoped storage key served by a new endpoint, or an allowlist extension in
`ProductImageService.read()`; the public contract would not change.
