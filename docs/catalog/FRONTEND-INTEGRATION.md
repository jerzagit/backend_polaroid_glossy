# Frontend integration — one product per size (Size → Style → Package)

Status: current as of 2026-10-18 — verified against the catalog controllers/services and migrations V23–V29 on `main`.

The catalog now models **one product per print size**. Each product has a
database-generated numeric `id` plus a stable human `code` (e.g. `ic`, `2r`,
`4r`). Inside a product you pick a **style** (`FULL` prints / `BORDER` adds the
classic Polaroid frame) and then a **package** (a quantity with its own
whole-package price). IC, 2R, and 3R offer both Full and Border; every other
size offers Full only.

Base URL: `https://polaroid-glossy-backend.fly.dev`

## Verified prices (IC, live)

| Package | Full | Border |
|---|---|---|
| 10 pcs | RM6.00 | RM5.00 |
| 20 pcs | RM12.00 | RM10.00 |
| 50 pcs | RM30.00 | RM25.00 |
| 100 pcs | RM55.00 | RM45.00 |

`pricingTiers` is still returned for backward compatibility (and the legacy
`sizeId` checkout path still prices from the per-print `print_sizes.price`), but
every size now has packages — price new checkouts from
`variants[].packages[]` and ignore `pricingTiers`.

## 1. What changed

- A size is now **one product** (`id` is a number, `code` is the slug) instead of
  a separate product per style. The storefront grid shows 10 products:
  `ic, 2r, 3r, 4r, 5r, 6r, polaroid-mini, strip-3, strip-4, a4`.
- `variants[]` (styles) hang off the product; each variant owns its `packages[]`.
- **All ids are numeric now**: `ProductListingResponse.id`,
  `ProductVariantResponse.id`, `ProductPackageResponse.id`. Checkout takes the
  numeric `productId` / `variantId` / `packageId`.
- Old slugs still resolve (see §7).

## 2. Catalog response shape

`GET /api/products` and `GET /api/products/{id}` (id may be the numeric id **or**
the `code` **or** a legacy slug):

```json
{
  "id": 1,
  "code": "ic",
  "name": "IC",
  "displayName": "IC (5.5 x 8.9 cm)",
  "dimensions": { "width": 5.50, "height": 8.90, "unit": "cm" },
  "price": 2.00,
  "pricingTiers": [],
  "active": true,
  "available": true,
  "variants": [
    {
      "id": 10,
      "style": "FULL",
      "label": "Full",
      "active": true,
      "coverImageUrl": "https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-full.webp",
      "packages": [
        { "id": 100, "quantity": 10, "price": "6.00", "currency": "MYR", "active": true }
      ]
    },
    {
      "id": 11,
      "style": "BORDER",
      "label": "Border (Polaroid)",
      "active": true,
      "coverImageUrl": null,
      "packages": [
        { "id": 101, "quantity": 10, "price": "5.00", "currency": "MYR", "active": true }
      ]
    }
  ]
}
```

Notes:
- Use `code` for pretty URLs (`/products/ic`) and `id` for API calls. Both work
  with `GET /api/products/{...}`.
- `price` is the legacy per-print base price. **Do not price from it** — use
  `variants[].packages[].price`.
- Package `price` is the **whole-package total** and is a decimal string (e.g. `"6.00"`).
- `variants[].coverImageUrl` is the style's own cover: an absolute HTTPS URL from that
  product's gallery, or `null` when it has none (fall back to the product-level `image`).
  List and detail responses return the same value; aliases return the filtered style's cover.
  This example shows the field inline in `variants[]` above.
- Public endpoints only return `active` products, variants, and packages.
- Only offer a style if `variant.active === true` **and** it has at least one
  package; otherwise the backend rejects checkout with `UNAVAILABLE`.
- When a size has a single style, you may hide the style picker (still send its
  `variantId`).

## 3. Selection UI

Size → Style (`FULL` = "Full", `BORDER` = "Border (Polaroid)") → Package.
Each style has its own package prices; never copy prices between styles.

## 4. Checkout payload

Submit the selected numeric ids (server calculates the payable amount — never
send a price):

```json
{
  "customerName": "...",
  "items": [
    {
      "productId": 1,
      "variantId": 10,
      "packageId": 100,
      "packages": 1,
      "imageUrls": ["..."],
      "customTexts": []
    }
  ]
}
```

- `packages` is the number of packages bought (default `1`). `quantity` is derived
  server-side as `package.quantity × packages`.
- Optionally send `expectedTotal` (whole order total incl. shipping) — if the
  catalog changed mid-journey the backend returns `409 PRICE_CHANGED` instead of
  silently charging a different amount.
- The legacy `sizeId` + `quantity` payload is still accepted during rollout, but
  it uses per-print pricing, so migrate to `packageId` to get the correct totals.

## 5. Order item response

Order items snapshot the selection (also stored on the order for history):

```json
{
  "sizeId": "ic",
  "sizeName": "IC (5.5 x 8.9 cm)",
  "variantId": "10",
  "variantLabel": "Full",
  "style": "FULL",
  "packageId": "100",
  "packageQuantity": 10,
  "packagePrice": 6.00,
  "packageCount": 1,
  "quantity": 10
}
```

`sizeId` is the product `code`; `variantId` / `packageId` are the numeric ids as
strings on the snapshot. `quantity` is total prints; `expectedImageCount`
follows the existing upload rules.

## 6. Price-change / unavailable handling

`POST /api/orders/{orderNumber}/pay` (and `/api/toyyibpay/create-bill`) return
`409` when the catalog changed:

```json
{ "success": false, "code": "PRICE_CHANGED", "currentTotal": "13.00", "message": "..." }
```

- `PRICE_CHANGED` → show the new total and ask the customer to reconfirm. On
  confirm call `POST /api/orders/{orderNumber}/confirm-price` (authenticated),
  then retry payment. The response returns the updated order.
- `UNAVAILABLE` → the package/style/size is disabled; tell the customer to
  cancel this journey and start a new selection.

The payment bill is always created from the server-calculated order total.

Once the customer proceeds into uploading/checkout, keep size, style, and package
fixed. Changing them must cancel the current journey (cancel the order) and start
a new one. The existing photo-upload flow is unchanged.

## 7. Legacy links

Old per-style slugs are merged into their size and **resolve to the canonical
product**, filtered to that style:

| Legacy slug | Resolves to | Style shown |
|---|---|---|
| `ic-border` | `ic` | BORDER |
| `2r-no-border` | `2r` | FULL |
| `2r-border` | `2r` | BORDER |
| `3r-no-border` | `3r` | FULL |
| `3r-border` | `3r` | BORDER |

So `GET /api/products/ic-border` returns the `ic` product with only the BORDER
variant (RM5.00/10, …). Existing carts/orders keyed by `sizeId` remain readable.

## 8. Seller dashboard endpoints (authenticated)

Reads: `ADMIN`, `MARKETING`. Writes: `ADMIN`. Path variables are numeric ids.

- `GET  /api/admin/catalog/products` — all sizes incl. inactive, with variants/packages
- `GET  /api/admin/catalog/products/{id}`
- `POST /api/admin/catalog/products` — add a size (`code`, `name`, `displayName`, optional `width`, `height`, `basePrice`, `description`, `tag`, `isActive`)
- `PATCH /api/admin/catalog/products/{id}` — edit size/dimensions/basePrice/active
- `POST /api/admin/catalog/products/{productId}/variants` — add style (`style`: `FULL`|`BORDER`, optional `label`, `sortOrder`, `isActive`)
- `PATCH /api/admin/catalog/variants/{variantId}` — enable/disable style, rename, reorder
- `PUT  /api/admin/catalog/variants/{variantId}/cover` — set or clear the style cover (`{"coverImageUrl": "https://…"}` / `{"coverImageUrl": null}`); the URL must already be in that product's gallery
- `POST /api/admin/catalog/variants/{variantId}/packages` — add package (`quantity`, `price` as whole-package total, `sortOrder`, `isActive`)
- `PATCH /api/admin/catalog/packages/{packageId}` — update `price`, `isActive`, `sortOrder` (quantity is immutable)
- `POST`/`PUT /api/admin/catalog/products/{id}/images`, `GET /api/products/{id}/images/{name}` — product gallery, see [PRODUCT-IMAGES.md](./PRODUCT-IMAGES.md)

Validation: positive quantities, positive prices, at most one variant per style
per product, no duplicate quantity within a variant. A style cover must be an
absolute HTTPS URL already in the product's gallery; removing that gallery image
clears the style cover in the same transaction. Disabling never deletes
historical order references; duplicate per-style sizes are **soft-retired**
(inactive), never deleted. Who changed a price and when is recorded automatically
(`created_by` / `updated_by` / timestamps).

Price edits appear on the storefront immediately — **no frontend deployment**.
The seller UI for all of this ships in this repo at
`frontend/src/app/dashboard/catalog`.
