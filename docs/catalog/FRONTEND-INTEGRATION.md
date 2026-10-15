# Frontend integration — Size → Style → Package

The backend now serves grouped catalog variants and prices whole packages.
Base URL: `https://polaroid-glossy-backend.fly.dev`

## Verified prices (IC, live)

| Package | Full (`ic-full`) | Border (`ic-border`) |
|---|---|---|
| 10 pcs | RM6.00 | RM5.00 |
| 20 pcs | RM12.00 | RM10.00 |
| 50 pcs | RM30.00 | RM25.00 |
| 100 pcs | RM55.00 | RM45.00 |

The old IC `pricingTiers` (including the 25 pcs tier) were removed. `pricingTiers`
is still present for other sizes that have not been migrated yet, so keep reading
it as a fallback for now.

## 1. Catalog response shape

`GET /api/products` and `GET /api/products/{id}` now include `dimensions`,
`variants`, `active`, and keep every existing field.

```json
{
  "id": "ic",
  "name": "IC",
  "dimensions": { "width": 5.50, "height": 8.90, "unit": "cm" },
  "price": 2.00,
  "pricingTiers": [],
  "variants": [
    {
      "id": "ic-full",
      "style": "FULL",
      "label": "Full",
      "active": true,
      "packages": [
        { "id": "ic-full-10", "quantity": 10, "price": "6.00", "currency": "MYR", "active": true }
      ]
    },
    {
      "id": "ic-border",
      "style": "BORDER",
      "label": "Border (Polaroid)",
      "active": true,
      "packages": [
        { "id": "ic-border-10", "quantity": 10, "price": "5.00", "currency": "MYR", "active": true }
      ]
    }
  ]
}
```

Notes:
- `price` is the legacy per-print base price. **Do not use it for IC pricing** —
  use `variants[].packages[].price`.
- Package `price` is the **whole-package total** and is a decimal string (e.g. `"6.00"`).
- Public endpoints only return `active` variants and `active` packages.
- Only offer a style if `variant.active === true` **and** it has at least one package;
  otherwise the backend rejects checkout with `UNAVAILABLE`.

## 2. Selection UI

Size → Style (`FULL` = "Full", `BORDER` = "Border (Polaroid)") → Package.
Each style has its own package prices; never copy prices between styles.

## 3. Checkout payload

Submit the selected ids (server calculates the payable amount — never send a price):

```json
{
  "customerName": "...",
  "items": [
    {
      "productId": "ic",
      "variantId": "ic-full",
      "packageId": "ic-full-10",
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

## 4. Order item response

Order items now snapshot the selection (also stored on the order for history):

```json
{
  "sizeId": "ic",
  "sizeName": "IC (5.5 x 8.9 cm)",
  "variantId": "ic-full",
  "variantLabel": "Full",
  "style": "FULL",
  "packageId": "ic-full-10",
  "packageQuantity": 10,
  "packagePrice": 6.00,
  "packageCount": 1,
  "quantity": 10
}
```

`quantity` is total prints; `expectedImageCount` follows the existing upload rules.

## 5. Price-change / unavailable handling

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

## 6. Fixed selections during checkout

Once the customer proceeds into uploading/checkout, keep size, style, and package
fixed. Changing them must cancel the current journey (cancel the order) and start
a new one. The existing photo-upload flow is unchanged.

## 7. Legacy links

- `GET /api/products/ic-border` still works and keeps its own identity, but its
  `variants` are the canonical `ic` BORDER variant with prices RM5.00/10, etc.
- Existing carts/orders keyed by `sizeId` remain readable.

## 8. Seller dashboard endpoints (authenticated)

Reads: `ADMIN`, `MARKETING`. Writes: `ADMIN`.

- `GET  /api/admin/catalog/products` — all sizes incl. inactive, with variants/packages
- `GET  /api/admin/catalog/products/{id}`
- `POST /api/admin/catalog/products` — add a size (`id`, `name`, `displayName`, `width`, `height`, `tag`, `isActive`)
- `PATCH /api/admin/catalog/products/{id}` — edit size/dimensions/active
- `POST /api/admin/catalog/products/{productId}/variants` — add style (`style`: `FULL`|`BORDER`, optional `label`, `sortOrder`, `isActive`)
- `PATCH /api/admin/catalog/variants/{variantId}` — enable/disable style, rename, reorder
- `POST /api/admin/catalog/variants/{variantId}/packages` — add package (`quantity`, `price` as whole-package total, `sortOrder`, `isActive`)
- `PATCH /api/admin/catalog/packages/{packageId}` — update `price`, `isActive`, `sortOrder` (quantity is immutable)

Validation: positive quantities, positive prices, no duplicate quantity within a
variant. Disabling never deletes historical order references. Who changed a price
and when is recorded automatically (`created_by` / `updated_by` / timestamps).

Price edits appear on the storefront immediately — **no frontend deployment**.
