# Photo upload reliability and scale plan

Status: planned; implement one phase at a time.
Date: 2026-11-01

## Goal

Prevent missing photos, reduce customer rework, and support orders containing 500–1,000 photos after capacity testing. Preserve Upload later throughout the rollout. Changes are required in both the customer frontend and the Spring Boot backend; the seller dashboard may need readiness/progress improvements.

## Findings to address

The inspected customer frontend's ordinary upload proxy limits attempts to 30 per 10 minutes per IP/order. The backend guest-upload path limits attempts to 10 per 10 minutes. These fixed limits conflict with larger orders.

The inspected checkout uploads up to five files concurrently and stops scheduling more after a failure, without automatic retries. Local photo IDs are not passed through the ordinary upload flow to the backend's existing duplicate-safe support. Checkout counts successful responses without a final authoritative completion check.

These are confirmed code weaknesses, not proof of the cause of every reported shortage. Before implementation, confirm the production customer frontend revision, inspect representative affected orders and request logs, and reproduce failures. Distinguish ordinary photos, strip-design assets, print copies and unique source files when calculating required uploads.

## Requirements that apply to every phase

- Keep intentional Upload later and recovery through the customer order page.
- Preserve completed uploads on the correct order and item.
- A selected file is not a successfully uploaded file. Count a photo as saved only after server acknowledgement and durable recording.
- Use stable photo identities across retries and page reloads; repeated requests must not add duplicate photos or consume extra capacity.
- Keep payment and upload readiness independent. Paid orders with missing photos remain Pending. Staff may start Processing only when payment and required photos are complete.
- Retain local files until the backend confirms completion. Handle browser storage failures explicitly.
- Warn before leaving while uploads are pending, but do not promise that browser closure or mobile termination can be prevented.
- Automatically recover from available local files. On another device, or after local storage is cleared/evicted, show the missing photos and allow the customer to supply them again.
- Preserve authentication, ownership checks, file validation and abuse protection. Do not solve legitimate upload failures by removing all limits.
- Keep ordinary uploads and strip asset uploads compatible; do not apply flat photo-count rules to strip designs incorrectly.

## Phase 1 — Fix current upload failures

### Customer frontend

- Pass each photo's stable ID, order ID and order-item ID through the upload proxy.
- Use bounded concurrency and retry transient network/server failures with backoff and jitter.
- Respect rate-limit responses and retry timing; do not endlessly retry permanent validation or authorization failures.
- Reconcile uncertain outcomes with the backend before retrying or reporting completion.
- Verify the server's saved-photo identities/counts before showing success or clearing the cart's local files.

### Backend

- Review fixed request limits and replace them with limits that accommodate authorized order quantities, concurrency and reasonable retries while retaining abuse protection.
- Complete duplicate-safe upload handling across supported authenticated/guest paths. Ensure replaying a completed upload remains safe, including at capacity and after completion.
- Review transaction boundaries, locks and concurrent metadata updates for lost updates, deadlocks and storage/database inconsistencies.
- Provide authoritative upload status and completion verification, including expected, saved and missing photo identities per item.
- Return consistent success, retryable-error and validation responses. Add structured upload failure logging without exposing tokens or signed URLs.

### Completion criteria

- Normal orders complete reliably, including quantities above the old fixed limits.
- Repeated requests for the same photo count once.
- Lost responses, temporary failures and concurrent uploads do not lose metadata or falsely report completion.
- Tests cover count semantics for ordinary photos, print copies and strip assets.
- Upload later still works before and after payment.

## Phase 2 — Automatic recovery

### Customer frontend

- Persist an order-linked upload queue with file data, stable photo IDs and per-photo state.
- Reuse existing IndexedDB storage where appropriate; check available capacity and surface persistence failures.
- On revisit, fetch server status and automatically resume only missing uploads whose files remain available.
- Retain successful/failed/pending state across refreshes and reconnects. Prevent multiple tabs from submitting competing queues where practical.

### Backend

- Support efficient status reconciliation for large upload manifests.
- Define upload authorization expiry/renewal and safe retry behaviour after a completed or interrupted session.
- Ensure status reports reflect durable records rather than client counters or temporary URLs.

### Completion criteria

- Refresh and network interruption resume without file reselection when local copies are available.
- Lost local storage and cross-device visits explain what is missing and retain the existing reupload fallback.
- Completed uploads are never restarted unnecessarily.

## Phase 3 — Clear customer experience

### Customer frontend

- Show separate selected, safely saved and remaining counts, plus per-photo retry/failure states.
- Display final completion only after backend confirmation.
- Warn on supported browser exits and guard accidental in-app navigation.
- Provide Continue uploading later, preserving queue and order state.
- Explain that switching devices or clearing browser storage may require selecting missing files again.

### Backend / seller dashboard

- Expose reliable progress and readiness fields for customer and seller views.
- Keep paused/incomplete uploads separate from payment status and order cancellation.
- Show sellers exactly why an order cannot enter Processing.

### Completion criteria

- Customers can distinguish selected files from saved files and deliberately pause without losing completed work.
- Navigation protection does not trap users or interfere with intentional payment redirects.
- Server verification remains the authority for completion.

## Phase 4 — Scale to 500–1,000 photos

### Customer frontend

- Upload directly to object storage using short-lived, authorized URLs supplied by the backend.
- Process/compress a small number of files at a time; avoid holding hundreds of decoded images or large previews in memory.
- Tune concurrency for connection/device conditions and refresh expired upload authorizations safely.
- Check local storage capacity and explain when automatic local recovery cannot be guaranteed.

### Backend / storage

- Introduce an authorized upload-session/manifest protocol: reserve a photo, issue an upload URL, verify stored content, then record completion.
- Bind object keys and upload rights to the correct customer, order, item and photo identity.
- Verify object existence, size and required content integrity/validation before counting it. Direct upload must not bypass image security checks.
- Configure appropriate storage CORS, expiration and orphan-object cleanup.
- Keep finalization idempotent and recover from an object being stored before the database acknowledgement succeeds.
- Avoid expensive per-photo full-order scans as batch sizes increase.

### Completion criteria

- 500- and 1,000-photo orders complete without excessive device memory usage or application-server load.
- Expired URLs, duplicate finalizations and interrupted uploads recover safely.
- Direct-storage uploads preserve all existing ownership and validation guarantees.

## Phase 5 — Stress testing and gradual rollout

- Agree on measurable targets for completion rate, latency, retry frequency, device memory and server/storage resource usage.
- Test 500 and 1,000 photos, multiple simultaneous customers, slow/flaky connections, lost responses, refreshes, mobile backgrounding/termination, and cross-device recovery.
- Test rejected files, storage exhaustion, URL expiry, repeated uploads, database/storage failures and partial completion.
- Test both Upload now and Upload later, paid and unpaid orders, ordinary products and strip designs.
- Roll out gradually using feature flags where practical and monitor results before increasing batch limits.
- Document rollback steps and preserve compatibility with already-created orders/upload sessions.

### Completion criteria

- Agreed reliability and performance targets pass before large batches are enabled broadly.
- Monitoring can identify the stage and reason for an incomplete upload.
- Customer recovery does not require reuploading photos the server already has.

## Delivery workflow

1. Start with Phase 1; do not combine all phases into one change.
2. Confirm the active customer frontend checkout and preserve unrelated local changes.
3. Create focused branches and coordinate backend/frontend API contracts.
4. Add regression tests, run relevant checks, and exercise real request flows locally with test orders.
5. Open PRs to main for the affected repositories.
6. Deploy compatible backend changes before dependent frontend changes; use an explicit checkpoint for each phase.
7. Verify production health and upload behaviour without modifying real customer orders as test fixtures.
8. Record results and remaining limitations here before starting the next phase.

## Progress

- [x] Phase 1: current upload failures — implemented; see results below
- [x] Phase 2: automatic recovery — implemented; see results below
- [ ] Phase 3: customer experience
- [ ] Phase 4: large-order architecture
- [ ] Phase 5: capacity testing and rollout

### Phase 1 results (2026-11-01)

Backend (`backend_polaroid_glossy`, branch `feat/upload-reliability-phase1`):

- One ordinary-photo path (`uploadPhoto`) behind `uploadFile`, `uploadFileForOrder`, `uploadWithId` and the controller. The order row is locked before items are loaded, so concurrent uploads cannot lose the JSON metadata append.
- `photoId` reaches every layer. A replay looks up the stored key before the capacity check, so a completed photo returns `alreadyUploaded` instead of a conflict, including at capacity.
- The fixed 10-per-10-minutes guest limit is replaced by a per-order attempt budget of `max(60, expected × 5)`, capped at 10,000, answered with `429` and `Retry-After`. Database capacity remains authoritative.
- `GET /api/files/upload-status` returns per-item expected/saved/remaining counts, saved photo identities and `complete`, computed from durable records.
- DRAFT orders and drafts that have not expired accept uploads (upload later before payment); closed orders still conflict. The upload token is retained after completion so a lost final response can be reconciled or replayed.
- Invalid stored photo metadata now fails loudly instead of being silently reset.

Customer frontend (`polaroid_glossy_backup`, same branch):

- `src/lib/reliable-upload.ts`: three concurrent uploads, exponential backoff with jitter, `Retry-After` honoured, permanent 4xx not retried, and status reconciled before and after the batch.
- Success requires the server to name each saved photo, not a client counter. `alreadyFull` allows a complete order whose remaining selections were refused for capacity to proceed instead of reporting a false failure.
- `stablePhotoId` derives identity from the file's metadata plus a sample of its bytes, so retrying a selection cannot store an already-saved photo twice.
- The Next.js upload proxy forwards `photoId` and raises its coarse per-IP ceiling; it no longer duplicates order verification.
- The pending-order hint carries the guest upload token so resuming payment can re-read saved-photo status.

Tests: 7 new backend cases (status counts for photos and strips, replay at capacity, draft uploads, attempt budget, email ownership) and 7 new frontend cases (identity stability, single upload per selection, retry, unrecorded photo, capacity-refused extras, rejected file, duplicate identities). Backend suite 99 green; frontend lint, typecheck and 29 tests green.

### Phase 1 limitations carried forward

- Photos uploaded before stable identities existed cannot be named individually; `uploadedPhotoIds` omits them and clients must trust `complete` for those orders. Re-selecting a subset of a partly uploaded batch can still mint a new identity for an already-saved photo, which the capacity refusal prevents from duplicating but Phase 2's persisted queue removes entirely.
- The attempt budget lives in an in-memory per-instance map: it resets on deploy and is not shared between Fly machines. Move it to a shared store during Phase 4.
- Deploy the backend before the customer frontend: the proxy no longer verifies order state on its own.
- Refresh still loses files because cart persistence strips `File` objects. That is Phase 2.

### Phase 2 results (2026-11-01)

Frontend only (`polaroid_glossy_backup`). The Phase 1 status endpoint already returns durable per-item counts and saved identities, which is exactly what reconciliation needs, so no new backend endpoint was added.

- `src/lib/upload-queue.ts` keeps an order-linked queue in its own IndexedDB database with one record per photo, so writing 500 photos never holds 500 buffers. A manifest read returns metadata only; file bytes are loaded just for the photos actually being resumed.
- Photo identities are stored, never re-derived. Re-deriving would have been wrong: the existing `photoStorage.ts` rebuilds files without `lastModified`, which would change a derived identity and duplicate a saved photo.
- `planResume` is a pure function over the server's status: it fills only the capacity still missing and counts photos with no local copy as unavailable instead of dropping them silently.
- Checkout persists the queue before uploading and clears it only after success. The order page resumes automatically once per visit and reports how many photos were resumed or still need selecting.
- Tests: 8 new cases covering only-missing resume, the capacity ceiling, unavailable counting, identity and metadata round-trip, clearing on completion, survival of a failed pass with per-photo state, and missing local storage. Frontend lint, typecheck and 37 tests pass.

### Phase 2 limitations carried forward

- Recovery is per browser profile. Another device, a cleared profile or evicted storage cannot resume, and the UI asks for those files again.
- Guest recovery beyond the 168-hour upload-token lifetime needs a new token, and only an admin reopen issues one. Signed-in customers are unaffected because their account authorizes the status call, but the order page is sign-in only, so a guest who loses the tab still cannot reach the automatic resume path.
- Queue writes race the upload on very large orders and are awaited before clearing so no orphaned records remain. Persisting local copies is pointless for a 1,000-photo order; skip or stream them in Phase 4.

## Browser constraints reference

- Page lifecycle and mobile termination: https://developer.chrome.com/docs/web-platform/page-lifecycle-api
- Browser storage quotas and eviction: https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria
