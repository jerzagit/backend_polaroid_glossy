# File Upload Security Audit — Backend Handoff

## Current State

**Frontend (this PR):** Added client-side MIME type, extension, and size validation before compression/upload. Only JPG, PNG, WEBP, and HEIC are accepted. Max 25MB per file.

**Backend (`FileService.validateImage()`):** Validates Content-Type header (`image/jpeg` or `image/png`) and file size (max 10MB). No magic bytes or content-level validation.

## Security Gaps on Backend

### 1. Content-Type is Spoofable (Critical)

`FileService.java:229-231` relies entirely on `MultipartFile.getContentType()` which is set from the HTTP `Content-Type` header. An attacker can send a `.exe` or `.html` file with `Content-Type: image/jpeg` and it will pass validation.

**Fix:** Validate file signature (magic bytes) instead of (or in addition to) the Content-Type header.

Magic bytes for allowed formats:
- JPEG: `FF D8 FF` (starts with bytes `0xFF 0xD8 0xFF`)
- PNG: `89 50 4E 47` (starts with `‰PNG`)
- WEBP: `52 49 46 46` (starts with `RIFF`) + `57 45 42 50` (at offset 8, `WEBP`)

### 2. No Image Dimension Validation

There is no check that the uploaded file has reasonable image dimensions. An attacker could upload a valid JPEG header with compressed garbage data (e.g., a "decompression bomb" or "pixel flood" attack).

**Fix:** After magic bytes validation, use `ImageIO.read()` or similar to verify the image can actually be decoded and has sane dimensions (e.g., max 10000x10000 pixels).

### 3. No File Extension Whitelist

Stored file paths at `orders/{orderId}/original/{uuid}.jpg` always use `.jpg`. This is safe since the extension is server-generated, but there is no validation that the original filename doesn't contain path traversal characters (`../`, null bytes, etc.).

**Fix:** Strip/reject any path traversal patterns from the original filename, or (better) don't use user-supplied filenames at all.

### 4. No Virus/Malware Scanning

Supabase Storage does not provide built-in virus scanning. Uploaded files are stored as-is. A malicious file that passes the above checks could still contain:
- Stolen credential data embedded in image pixels (steganography)
- Malware in image metadata (EXIF payloads)
- ZIP bombs disguised as valid images

**Fix:** Integrate a virus scanning service (e.g., ClamAV, AWS GuardDuty) or use Supabase Edge Functions with a scanning hook. At minimum, re-encode images server-side using a trusted library to strip all metadata and ensure valid encoding.

### 5. SVG / Scriptable Upload Vector (Medium)

The backend currently only allows `image/jpeg` and `image/png`, so SVG XSS (CVE-style stored XSS via SVG) is not a direct risk. However, if the Content-Type spoofing gap (#1) is exploited, an attacker could upload an `.html` or `.svg` file with a spoofed Content-Type and then access it via signed URL, potentially executing scripts in the admin dashboard context.

**Fix:** Magic bytes validation (#1) closes this vector. Also ensure storage bucket serves files with `Content-Disposition: attachment` for unknown types.

## Supabase Storage Security Notes

- **Bucket:** `polaroid-glossy` is set to private (no public access).
- **Access:** Signed URLs with 3600s expiry are returned by the backend.
- **RLS:** Verify that RLS policies on `storage.objects` table restrict access to:
  - `INSERT` only for authenticated users (backend service role)
  - `SELECT` only via signed URLs or authenticated requests
  - No public/anonymous policies
- **CORS:** Ensure CORS on the Supabase project only allows your frontend domain(s).
- **Malware scanning:** Supabase does **not** have built-in malware scanning. This must be implemented at the application layer.

## Summary

| Priority | Issue | Impact |
|----------|-------|--------|
| **Critical** | Content-Type spoofing (no magic bytes) | Any file type can be uploaded |
| **High** | No image dimension validation | Decompression bombs |
| **Medium** | No malware scanning | Persistent malicious payloads |
| **Low** | No filename sanitization | Path traversal (mitigated by server-generated paths) |

## Recommended Backend Changes

1. Add magic bytes validation in `FileService.validateImage()` using `ImageInputStream` or Apache Tika
2. Add `ImageIO.read()` try-catch to verify image is decodable with sane dimensions
3. Consider adding ClamAV or similar scanning via Supabase Edge Function
4. Strip EXIF/metadata on upload by re-encoding the image server-side
