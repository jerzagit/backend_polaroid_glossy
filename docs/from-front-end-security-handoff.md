# File Upload Security Audit — Backend Handoff

> **Status: updated 2026-10-18.** This supersedes the audit as written on 2026-06-27.
> The backend has since implemented most of what this document asked for, and storage has
> moved from Supabase to Cloudflare R2, so the original "current state" and gap list no
> longer describe the code. Original findings are kept and labelled **Historical**;
> each one now has a resolution status. Open items remain — see [Remaining Open Items](#remaining-open-items).

Audience: the backend team (closing the loop on the frontend's upload audit) and the
frontend team (who depend on these upload rules). Verified against `FileService`,
`FileController`, `SecurityConfig`, `storage/R2StorageService` and `application-prod.yml`.

## Current State

**Frontend (unchanged):** client-side MIME type, extension, and size validation before
compression/upload. Only JPG, PNG, WEBP, and HEIC are accepted. Max 25MB per file.

**Backend (`FileService.validateAndProcessImage`):** server-side validation no longer
trusts the request. In order it enforces:

```text
file present and non-empty
size <= 25 MB                        (MAX_FILE_SIZE)
magic bytes in {jpeg, png, webp}     (detectImageFormat, ALLOWED_FORMATS)
ImageIO can decode it
decoded size <= 100 MB               (width * height * 4, MAX_DECODED_MEMORY)
width and height <= 10000 px         (MAX_IMAGE_DIMENSION)
```

Accepted images are then **re-encoded** to JPEG or PNG (quality 0.95, alpha preserved as
PNG), so what lands in storage is not the uploaded byte stream.

**Historical:** the original audit said the backend only checked the `Content-Type`
header and capped files at 10MB via a method called `validateImage()`. That method no
longer exists and nothing in the upload path reads `MultipartFile.getContentType()` for
accept/reject decisions.

## Endpoint Context (why these checks carry the weight)

`POST /api/files/upload` is `permitAll` — anonymous callers can reach it. The gate is
application-level, not authentication-level:

- guest: `orderId` + `customerEmail` matching the order + a valid `uploadToken`
  (SHA-256 hash compared with `MessageDigest.isEqual`, expiry default 168h, cleared once
  the order's uploads are complete);
- logged-in: JWT plus order ownership (or staff role);
- anonymous rate limit: 10 uploads per 10 minutes per client IP + order;
- per-item capacity and order state (`PENDING`/`PROCESSING`, not failed/cancelled/refunded)
  are enforced before the bytes are touched.

So the correct framing is: **upload is public but token-gated**, and the validation above
is the only thing standing between an anonymous caller and storage.

## Finding Status

### 1. Content-Type is spoofable — **FIXED**

**Historical finding:** `FileService.java:229-231` relied on `MultipartFile.getContentType()`;
a `.exe` or `.html` sent as `image/jpeg` passed.

Current: the request `Content-Type` is ignored for validation. The format is detected from
magic bytes (`FF D8 FF` JPEG, `89 50 4E 47` PNG, `RIFF`+`WEBP` WebP) and anything else is
rejected with 400 (`magic bytes mismatch`). The image is re-encoded afterwards, which also
removes trailing polyglot payloads.

### 2. No image dimension validation — **FIXED**

Current: `ImageIO.read` must succeed, decoded size is capped at 100MB
(`width*height*4`), and each side is capped at 10000px. Decode failures return 400.

### 3. No filename sanitization — **FIXED (by construction)**

Current: storage keys are entirely server-generated —
`orders/{orderNumber}/{orderItemId}/{uuid}.jpg` (or `upload-{photoId}.jpg`). The client's
filename never reaches the key. `originalFilename`, when stored for strip assets, has
control characters stripped and is truncated to 255 chars, and `photoId` must match
`[a-zA-Z0-9-]{1,64}`.

### 4. No virus/malware scanning — **OPEN**

Still no AV scanner. Nothing calls ClamAV/GuardDuty and there is no scanning hook.

Mitigations now in place, which reduce but do not close this: magic-byte allow-list
(JPEG/PNG/WEBP only), full decode, and server-side re-encode (strips EXIF and appended
metadata, so EXIF-payload and steganographic *delivery* vectors are largely removed).

**Recommended next step:** scan on upload (or asynchronously before an order reaches
production status) and quarantine on detection. Until then, treat the bucket as
untrusted content and keep it private (it is).

### 5. SVG / scriptable upload vector — **FIXED**

Current: the allow-list is JPEG/PNG/WEBP after decoding, so SVG and HTML cannot pass, and
there is no path to serve attacker-chosen content types. Residual (low): objects are
stored with `Content-Type: image/jpeg|png` and **no** `Content-Disposition: attachment`;
because only raster image types can be stored, inline rendering is acceptable. If a new
format is ever allowed, revisit this and add attachment disposition for unknown types.

## Storage Notes (Cloudflare R2, not Supabase)

- **Provider:** `app.storage.provider=${STORAGE_PROVIDER:r2}` in `application-prod.yml`;
  `R2StorageService` talks to R2 through the S3 API with a presigner (`path-style`
  default true, region `auto`). Dev defaults to local filesystem; an S3 strategy exists.
- **Bucket privacy:** the bucket must be private; the app never returns public object
  URLs, only presigned GETs.
- **URL expiry:** 300 seconds — `FileService.IMAGE_URL_EXPIRATION_SECONDS = 300`,
  `OrderMapper.IMAGE_URL_EXPIRATION_SECONDS = 300`,
  `R2StorageService.DEFAULT_SIGNED_URL_EXPIRATION_SECONDS = 300`.
- **Credentials:** presigning needs `STORAGE_R2_ACCESS_KEY` / `STORAGE_R2_SECRET_KEY`;
  without them `getSignedUrl` throws `R2 signed URL credentials are not configured`.
- **Legacy / Historical:** the Supabase bucket `polaroid-glossy`, Supabase `storage.objects`
  RLS guidance, and `SUPABASE_SIGNED_URL_EXPIRATION=3600` below no longer apply. The
  `supabase.*` properties persist in `application.yml` / `application-uat.yml` but are
  unused by storage code.
- **CORS:** object CORS on R2 only needs to allow the frontend if the browser fetches
  presigned URLs cross-origin; the backend CORS allow-list is `CORS_ORIGINS`
  (comma-separated).

**Historical** (kept for traceability — do not action): bucket `polaroid-glossy` private;
signed URLs with 3600s expiry; RLS on `storage.objects` (INSERT only for authenticated
backend, SELECT only via signed URLs, no public policies); Supabase project CORS limited
to the frontend domain; Supabase has no built-in malware scanning.

## Remaining Open Items

| Priority | Item | Impact | Status |
|---|---|---|---|
| **High** | `GET /api/orders?userId=<uuid>` is public and returns that user's orders; `GET /api/reviews` exposes `userId` publicly | Order enumeration for any known customer UUID | **Open — backend fix required** |
| **Medium** | No malware/AV scanning on upload | Persistent malicious payloads served from a private bucket | **Open** |
| **Low** | Anonymous rate limiting is an in-process `ConcurrentHashMap` in `FileService` (10 per 10 min per IP+order) | Limit is per instance; horizontal scaling or restarts weaken it | **Open (known limitation)** |
| **Low** | No `Content-Disposition: attachment` on stored objects | Inline rendering only; currently safe because only raster images can be stored | **Accepted** |

The order-enumeration item is a separate track from this upload audit and is tracked in
`docs/production-security-checklist.md` under "Outstanding Fix".

## Original Recommendations — What Happened

1. ~~Add magic bytes validation in `FileService.validateImage()`~~ — **done**, in
   `validateAndProcessImage` (method renamed).
2. ~~Add `ImageIO.read()` with sane dimensions~~ — **done** (10000px per side, 100MB
   decoded cap).
3. ~~Add ClamAV or similar scanning~~ — **not done** (see Open Items).
4. ~~Strip EXIF/metadata by re-encoding~~ — **done** (all uploads re-encoded to
   JPEG/PNG).
