# Strip products: designs, photo slots, and seller downloads

Status: backend implemented, frontend editor not started. Product behavior is agreed; the API contracts below are now implemented in the backend (strip checkout, per-slot asset upload, seller ZIP/slot exports, `GET /api/orders/capabilities`). Frontend integration is outstanding — see "Frontend integration work".

Updated: 2026-10-17.

## Backend task summary

Implement multi-design strip orders without customer cropping or strip image generation:

1. Extend order creation/storage with nested designs, copy counts, numbered slots, and reserved source assets.
2. Return stable asset mappings and accept idempotent uploads against those assets.
3. Return the design structure in customer and seller order details.
4. Provide seller downloads grouped by design, with slot-numbered filenames and a print manifest.
5. Validate package allocation and source-asset readiness server-side while preserving ordinary product behavior.

The technical printing operator handles cropping and final arrangement in the existing printer system. No crop fields, crop processing, crop derivatives, composite strip generation, or printer sheet layout are part of this task. Existing R2 files do not need renaming.

## Scope and agreed behavior

Apply only to product codes `strip-3` (3 slots) and `strip-4` (4 slots). Other products retain their existing upload, pricing, and download behavior.

The customer buys a package of physical prints, then distributes those prints across designs. Each design has ordered photo slots and a positive integer copy count. A photo may be reused within or across designs. Copies repeat the entire design; they do not require more uploads.

Example: a five-print `strip-3` package contains Design A with 3 copies and Design B with 2 copies. Each design has 3 slots. Six different photos require six uploads; repeated photos can reduce that number. The customer receives five physical strips, not six prints or fifteen uploaded files.

The seller's existing printer system assembles the strips. Do not build a complete strip renderer, composite image, or imposed print sheet. The seller needs individual image files, design membership, slot numbering, and copy counts. Slot 1 is the top photo; subsequent slots proceed downward.

## R2 filenames: no rename required

Keep R2 object keys opaque, stable, and server-generated. Existing objects do not need renaming or moving for this feature. Design and slot membership belong in database records, not filenames or upload arrival order.

Suggested key for NEW source assets (illustrative, not a migration requirement):

```text
orders/{orderId}/assets/{assetId}/original.jpg
```

Use the actual validated file format/extension; do not rename PNG/WebP bytes to `.jpg`. Keep original filename as optional display metadata, never as the storage identity. Store the R2 key in the asset record; generate usable download URLs when responding rather than using expiring signed URLs as permanent identifiers.

Why not encode design, slot, or copies into the R2 key?

- One asset can occupy several slots and designs.
- Reordering a slot or changing copies should update metadata, not move objects.
- Stable asset IDs make upload retries and references reliable.
- Friendly seller filenames can be generated when exporting a ZIP without changing storage keys.

## Backend data model

Use equivalent tables or a validated structured document; exact schema is the backend team's choice.

| Entity | Required fields / meaning |
|---|---|
| Order item | Existing catalog/package snapshot, authoritative physical print quantity, strip slot-count snapshot, design schema version |
| Strip design | Stable server ID, order-item ID, display order, copies, revision |
| Design slot | Design ID, position (1-based), asset ID; no crop fields |
| Asset | Stable server ID, owning order/item, client upload reference, R2 key, upload status, validated MIME type, pixel dimensions, orientation normalization metadata |

Enforce unique slot position within a design. Asset reuse is allowed across designs within the same order item. Cross-item reuse is outside this proposal and should not be accepted implicitly. Do not merge different designs just because their product/package matches.

Keep one purchased package item containing several designs. Price the package once; do not charge each design as a separate package. Derive quantity using the existing catalog contract (`package.quantity * packages`), not a client-supplied total. The current frontend supports one package per journey; this feature need not expand that scope.

## Proposed order creation request

Extend existing `POST /api/orders` item fields. Numeric IDs below are illustrative: resolve actual IDs from the catalog. Product code `strip-3` is resolved server-side from `productId`.

```json
{
  "items": [
    {
      "productId": 31,
      "variantId": 310,
      "packageId": 3105,
      "packages": 1,
      "stripDesign": {
        "schemaVersion": 1,
        "assets": [
          { "clientAssetId": "photo-a" },
          { "clientAssetId": "photo-b" },
          { "clientAssetId": "photo-c" },
          { "clientAssetId": "photo-d" },
          { "clientAssetId": "photo-e" },
          { "clientAssetId": "photo-f" }
        ],
        "designs": [
          {
            "clientDesignId": "design-a",
            "copies": 3,
            "slots": [
              { "position": 1, "clientAssetId": "photo-a" },
              { "position": 2, "clientAssetId": "photo-b" },
              { "position": 3, "clientAssetId": "photo-c" }
            ]
          },
          {
            "clientDesignId": "design-b",
            "copies": 2,
            "slots": [
              { "position": 1, "clientAssetId": "photo-d" },
              { "position": 2, "clientAssetId": "photo-e" },
              { "position": 3, "clientAssetId": "photo-f" }
            ]
          }
        ]
      }
    }
  ]
}
```

Client references are correlation identifiers only. The backend creates authoritative IDs and returns mappings for order item, designs, and assets. Upload requests use the returned asset IDs. Map by references/IDs, not response array position.

### Proposed response shape

Extend the existing `success` / `order` envelope. For order items, expose `stripDesign.schemaVersion`, `slotCount`, `assets`, and `designs`. Preserve existing catalog/package snapshot fields. Example nested structure (one-design allocation shown for brevity):

```json
{
  "id": "order-item-123",
  "sizeId": "strip-3",
  "quantity": 5,
  "stripDesign": {
    "schemaVersion": 1,
    "slotCount": 3,
    "assets": [
      {
        "id": "asset-123",
        "clientAssetId": "photo-a",
        "status": "pending",
        "previewUrl": null,
        "downloadUrl": null
      }
    ],
    "designs": [
      {
        "id": "design-123",
        "clientDesignId": "design-a",
        "label": "A",
        "revision": 1,
        "copies": 5,
        "slots": [
          { "position": 1, "assetId": "asset-123" },
          { "position": 2, "assetId": "asset-123" },
          { "position": 3, "assetId": "asset-123" }
        ]
      }
    ]
  },
  "allocatedPrintCount": 5,
  "expectedAssetCount": 1,
  "uploadedAssetCount": 0,
  "fulfillmentReady": false
}
```

After upload, the asset status becomes `uploaded`, authorized URLs become available, `uploadedAssetCount` becomes 1, and `fulfillmentReady` becomes true if all validation passes. Readiness describes image fulfillment, not payment confirmation or permission to ship. Keep those existing checks separate.

Customer and seller detail endpoints must return the same assignment structure. Do not expose R2 credentials. Include original filenames only as optional display metadata, not as identifiers.

## Order-first upload lifecycle

Preserve the existing create-order-before-upload flow:

1. Validate package, design allocation, and all slot references; reserve pending asset records transactionally with order creation.
2. Return `clientAssetId -> assetId` and `clientDesignId -> designId` mappings.
3. Extend the existing browser `/api/upload` proxy and backend `POST /api/files/upload` contract to accept `assetId` alongside the existing order and order-item identifiers.
4. Verify the asset belongs to that order item and the requester is authorized. Validate and store each unique source image once.
5. Make retrying the same reserved asset idempotent: a successfully completed upload must not append another image or increase counts. Reject a different replacement once finalized; any later edits require an explicit revision workflow.
6. Compute readiness from successfully uploaded assets referenced by all valid slots, not a flat images-array length.

Concurrent upload completion order never determines slot order. Preserve existing order/payment-state upload restrictions and server-side ownership checks. Reuse current file validation; do not accept arbitrary client R2 keys or URLs as owned assets.

Proposed successful upload response extends existing fields with `assetId`, `orderItemId`, `uploadedAssetCount`, `expectedAssetCount`, and `fulfillmentReady`. Return those same identifiers/counts for an already-completed identical retry. Use a request idempotency key for order creation so a timed-out retry cannot create duplicate packages; do not recover a strip order solely by matching product, quantity, and price because its designs may differ.

For strip responses, expose explicit counters such as:

```json
{
  "quantity": 5,
  "allocatedPrintCount": 5,
  "expectedAssetCount": 6,
  "uploadedAssetCount": 6,
  "designCount": 2,
  "fulfillmentReady": true
}
```

`expectedAssetCount` is the distinct referenced source-asset count. Do not multiply it by copies or conflate it with filled slot count. Avoid changing legacy `expectedImageCount` semantics globally; strip clients/proxies must use the new fields. Existing flat image counts are insufficient to establish strip readiness.

## Server validation

- Strip metadata is supported only for `strip-3` and `strip-4`.
- At least one design, at most the purchased print quantity; positive integer copies.
- Sum of design copies equals authoritative purchased print quantity.
- Exactly 3 or 4 slots per design with unique contiguous positions 1..N.
- Every slot references a declared/reserved asset of this order item. Reuse is allowed; undeclared and unreferenced asset declarations are rejected.
- Unique client design/asset references within the item and bounded request sizes.
- An order can be created with pending assets, but cannot be marked ready for production until all referenced assets are complete.
- Invalid allocation/slots return a clear validation error; never silently add copies, remove designs, or rearrange slots.
- Preserve existing authoritative pricing and product availability checks.

## Seller dashboard

Extend the existing seller/admin order response with nested designs, ordered slots, asset preview/download references, and copies. Use the same structure in the customer order detail response with appropriate authorization.

Display for the example:

| Design | Slot 1 (top) | Slot 2 | Slot 3 (bottom) | Copies |
|---|---|---|---|---|
| A | Photo A thumbnail | Photo B thumbnail | Photo C thumbnail | 3 |
| B | Photo D thumbnail | Photo E thumbnail | Photo F thumbnail | 2 |

Show package total: **5 physical strips / 2 designs**. Include each slot's download, a per-design download, and a download-all action. Repeated assets still appear in every assigned slot. Pending/missing assets remain visible as missing slots; disable the complete production ZIP until the item is ready and eligible under existing production rules.

Do not infer membership from a filename, flat gallery, timestamp, or upload order.

## ZIP export and manifest

Extend the existing seller export mechanism; exact admin endpoint path should follow the backend's current conventions. Generate safe entry names from server-controlled order/design labels and slot numbers.

```text
ORDER-123/strip-3/
  Design-A__3-copies/
    slot-1.jpg
    slot-2.jpg
    slot-3.jpg
  Design-B__2-copies/
    slot-1.jpg
    slot-2.jpg
    slot-3.jpg
  print-instructions.csv
```

The folder's copy count applies to the whole strip. Do not duplicate folders or source files three times to represent three copies. However, when Photo A occupies slots 1 and 3, include its bytes under BOTH slot filenames. This gives the seller a complete ordered set without needing to resolve reuse references.

Manifest: one row per slot, with columns `orderNumber`, `orderItemId`, `productCode`, `designId`, `designLabel`, `designRevision`, `copiesOfDesign`, `slotPosition`, `filename`, `assetId`. The copy count is repeated for context; it must NOT be summed across slot rows. Include a separate one-row-per-design summary for physical print totals.

Resolve signed URLs at download time or read directly from R2 using server credentials. Never put credentials or temporary URLs in the manifest. Export from one consistent design revision; include that revision in the export metadata. Stream/limit downloads as appropriate instead of buffering an unbounded ZIP.

## Cropping is excluded

Confirmed decision: the technical printing operator handles cropping in the printer system. Download the stored source image for each numbered slot without a new crop, resize, or recompression during export. Source here means the accepted uploaded file; existing client HEIC conversion/compression may already have transformed the device original.

Do not add customer crop controls, crop coordinates to the DTO/schema, cropped derivatives, or a strip renderer. Show source thumbnails with their full contents (`object-fit: contain`) and clearly label slot order. The preview communicates assignment and order, not an exact final print crop.

## Frontend integration work

- Keep the purchased package fixed; allow designs and copies whose total equals package prints.
- Add per-slot upload/reuse controls and explicit move-up/down actions. No customer cropping.
- Persist unique assets plus design/slot metadata and copy counts. Restore that structure across refreshes.
- Store all designs under the existing package cart item; do not require multiple package cart entries.
- Replace strip-only assumptions equating `photos.length`, quantity, and expected upload count in `src/app/page.tsx` and `src/app/api/orders/route.ts`.
- Extend `src/app/api/upload/route.ts` to forward stable asset IDs and use strip readiness/counts.
- Update customer order details and seller dashboard to display nested designs and numbered slots.
- Continue using Next.js proxies and `src/lib/backend.ts`; writes fail when the backend is unavailable.

## Compatibility and rollout

This proposal supersedes flat image-count assumptions for new strip orders only. Do not apply old handoff formulas such as selected photos multiplied by quantity to these designs.

Deploy backend support before enabling the editor. Require an explicit supported contract/version signal or coordinated feature flag; never silently submit strip designs to an endpoint that ignores unknown fields. Existing ordinary orders stay unchanged. Legacy strip orders without explicit design/slot metadata need seller review; do not invent arrangements from the existing image list.

Freeze production metadata when the seller begins fulfillment, or implement explicit revisions and invalidate stale downloads on changes. Existing downloaded files cannot be revoked, so changes after production starts need an explicit seller process.

## Acceptance checks

1. Five-print strip-3: A x3 plus B x2 validates and exports two folders with three ordered files each.
2. One design x5 needs only its referenced unique photos; never fifteen uploads.
3. Five designs x1 with all different photos accept fifteen assets.
4. A/B/A uses two stored assets but exports three slot files in the correct order.
5. One shared photo across multiple designs uploads once and appears at every assigned slot.
6. Allocations below/above five, invalid copies, missing/duplicate positions, and cross-order asset references are rejected.
7. Concurrent uploads and retries preserve ordering, asset count, and copy totals.
8. Refresh and partial-upload recovery preserve all designs and slot mappings.
9. Missing files block complete production export; failed downloads return an error rather than a silently incomplete ZIP.
10. strip-4 requires four slots; ordinary product checkout and downloads remain unchanged.
11. Package price is charged once regardless of number of designs.
12. Slot exports preserve accepted source-image bytes; no crop or recompression is applied. Repeated slot entries reference/download the correct same source.

## Backend team confirmation needed

- Existing order DTO/schema, upload response shape, and seller export endpoint to extend.
- The response envelope and capability signal for the new versioned strip contract.
- Existing production lock/status rules and whether revisions are needed before that lock.

## Local development note

`application-dev.yml` sets `spring.flyway.enabled: false` and relies on `ddl-auto: update`, which only creates ORM-mapped tables. `strip_checkout_requests` is created by `V28__Strip_designs_and_checkout_idempotency.sql` and has no JPA entity, so it will not exist in a dev database until the migration is applied by hand:

    psql "$DEV_DATABASE_URL" -f src/main/resources/db/migration/V28__Strip_designs_and_checkout_idempotency.sql

Without it, strip checkout fails with `relation "strip_checkout_requests" does not exist`. The `order_items.strip_design` column is mapped by the entity and is added automatically by `ddl-auto: update`. Production applies V28 through Flyway on boot.
