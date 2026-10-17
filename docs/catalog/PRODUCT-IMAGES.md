# Product images

Status: current as of 2026-10-18 — verified against `ProductImageController` / `ProductImageService` and migrations V26–V29.

In Seller Dashboard → Catalog, select a product and use **Product images** to upload an image, add an HTTPS image URL, remove an image, or choose **Set as cover**. Changes save immediately. The first image is the cover; removing it promotes the next image. Removing all images clears the cover.

ADMIN can edit; MARKETING can view. Existing product metadata and product order are unchanged. Images remain in `product_meta.images` as an ordered JSON array. Both public and admin product responses expose that order in `images`, with the first URL in `image`.

## API

- `PUT /api/admin/catalog/products/{id}/images`: JSON `{ "images": ["https://..."] }`; replaces the ordered list. Empty list clears it. At most 12 distinct URLs.
- `POST /api/admin/catalog/products/{id}/images`: multipart field `file`; appends an uploaded image and returns the updated product. The first upload on an empty product becomes the cover.
- `GET /api/products/{id}/images/{name}`: public catalog image bytes, restricted to an image still associated with that product. This endpoint cannot read customer order photos.

Uploads accept decoded JPEG, PNG and WebP images up to 10 MB and 20 megapixels. Files are re-encoded as PNG, stripping embedded metadata. Production stores them in the existing R2 bucket under `catalog/{productId}/{uuid}.png`; local development uses local storage. Stable backend URLs avoid expiring catalog thumbnails. `PUBLIC_API_URL` configures the production API origin (defaults to the current Fly app URL); other environments can set `app.public-api-url`.

Removing an image removes its catalog association; the object is retained in storage. Removing external image URLs does not delete externally hosted files. Upload objects are cleaned up if the database transaction rolls back. If a style cover (`product_variants.cover_image_url`) points at a removed image, that style cover is cleared in the same transaction, so no variant is left pointing at a URL the public endpoint now 404s.

This feature builds on the catalog editor from `feat/product-catalog-api`, which is merged into `main` (catalog migrations V18–V22, then the canonical products/variants/packages schema in V23–V29). Nothing extra is needed to deploy images or style covers: Flyway applies the migrations on boot. `app.public-api-url` (env `PUBLIC_API_URL`) must be the externally reachable backend origin, because uploaded image URLs are built from it.
