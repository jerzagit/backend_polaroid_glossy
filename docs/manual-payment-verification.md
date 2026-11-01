# Manual payment verification

Seller order details now show independent payment and photo readiness.

1. A signed-in customer submits bank-transfer proof using the existing upload endpoint, or an admin attaches the receipt received through WhatsApp in the seller dashboard.
2. Submission records the receipt and reference and shows **Awaiting verification**. It never marks an order paid.
3. An admin opens the receipt, checks the bank payment, and chooses **Verify payment**. The modal requires the full amount received (including delivery), a transaction reference, and an explicit funds-received checkbox. Notes are optional.
4. Approval records the admin, time, amount, reference and receipt snapshot. The order becomes paid but remains Pending. Payment confirmation and shop alert emails are sent once on the unpaid-to-paid transition.
5. Once all required photos are present, staff choose **Start processing**, then confirm the existing status modal. The backend enforces payment and photo readiness.

## Rejection and replacement

Rejection requires a reason and retains the receipt and review history. The customer or admin can upload a replacement. A review of an outdated receipt is rejected; refresh and inspect the replacement before approving. Approved payments cannot be rejected or have their proof replaced.

The payment review history includes previous receipts, submission source, actor, time, references, amounts and review notes. Receipt links are signed and short-lived; use **Refresh proof** to renew them.

## Access and API

- Only ADMIN can verify/reject payments or read the complete receipt review history. MARKETING and PACKER can view readiness but cannot approve or upload payment proof on a customer's behalf.
- `POST /api/files/payment-proof` accepts multipart `file`, `orderId`, and optional `reference`. Customer uploads are tagged `SYSTEM_UPLOAD`; admin uploads are tagged `WHATSAPP`.
- `GET /api/files/payment-proof/{orderId}` returns the current `proofKey`, signed `proofUrl`, and review history to admins.
- `POST /api/admin/orders/{orderNumber}/verify-payment` accepts `action`, `proofKey`, and either approval fields (`amountReceived`, `reference`, `fundsConfirmed`, optional `notes`) or rejection `reason`.
- Approval/rejection and uploads lock the order before reading its payment state. Repeat approval returns the saved paid order without new audit entries or notifications.
- The older payment-status endpoint cannot mark orders PAID. Reopening an order must be done before verification; the `paymentReceived` shortcut is rejected.
- Cancelled, expired, refunded and fulfilled orders cannot use this verification flow directly. Reopen eligible orders first.

## Release notes

Deploy the backend with migration **V32** before deploying the dashboard. Existing pending proof is marked Awaiting verification. Historical verification amounts and actors are not guessed.

Automatic reconciliation now leaves paid orders Pending, including gateway payments and later photo uploads; staff explicitly start processing. Existing Processing orders are unaffected. Partial payments and overpayments are not supported by this approval action: the entered amount must equal the current full order total.

Validation includes database migration/persistence, permission checks, stale receipts, duplicate approval, retained rejection history, receipt upload sources, payment/photo processing guards, and a mocked browser flow covering upload, rejection, approval, cancellation and packer permissions.
