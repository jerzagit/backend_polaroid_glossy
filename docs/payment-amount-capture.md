# Payment amount capture — product, delivery and total

**Status:** proposed
**Owner:** backend
**Frontend dependency:** `polaroid_glossy_backup` → `src/app/admin/orders/page.tsx`, `src/app/profile/orders/[id]/page.tsx`
**Next migration slot:** `V31`

## 1. Problem

An order stores what it *should* cost but never what the customer actually *paid*.

Evidence from production (`PG77514747E4C3`, 2026‑10‑30):

| field | value |
|---|---|
| `subtotal` | 4.00 (Polaroid Mini / BORDER, 1 × 10 prints) |
| `shipping` | 7.00 |
| `total` | 11.00 |
| `payment_method` | `bank_transfer` |
| `payment_proof_url` / `payment_reference` | null |
| `paid_at` | null |
| `status` | CANCELLED — auto-cancelled 24h later by `PaymentCleanupService` |

The order was priced correctly (prints + delivery). Nothing in the schema records the transferred amount, so:

- the seller cannot tell RM4.00 + RM7.00 from a single RM11.00 blurb in the dashboard,
- a short transfer (customer sends RM4.00, the print price only) is invisible and silently approvable,
- `verifyPayment` APPROVE (`OrderService.java:421-439`) and `PATCH /api/admin/orders/{id}/payment-status` (`OrderService.java:344-399`) set `PAYMENT_STATUS = PAID` with no amount check,
- the customer proof form (`FileController.java:56-62` → `FileService.java:165-180`) accepts only `file` + `reference`; there is no amount to compare.

ToyyibPay is already safe: `PaymentService.java:86` bills `order.getTotal()` and `PaymentService.java:197` rejects a callback whose amount ≠ total. This work is about **manual bank transfer** and about **showing the split in the dashboard**.

## 2. Required behaviour

1. Every order keeps its three priced components: `subtotal` (prints), `shipping` (delivery), `total` (= subtotal + shipping). *Already implemented — no change.*
2. Every payment records **what was actually received** (`payment_amount`) plus who recorded it and when.
3. The amount is captured in all three entry points: customer proof upload, admin approve/reject, gateway callback.
4. Approving a bank transfer whose received amount is **below** `order.total` is refused by default, and only possible with an explicit override that is written to status history.
5. `GET /api/admin/orders` returns `subtotal`, `shipping`, `total`, `paymentAmount`, `paymentVariance` so the dashboard can show:

```
Subtotal (prints)   RM 4.00
Delivery            RM 7.00
Total               RM 11.00
Received            RM 11.00        ← new
```

6. Legacy orders keep working: `payment_amount` is nullable, absent → dashboard prints "Not recorded" instead of RM 0.00.

## 3. Database

New file `src/main/resources/db/migration/V31__Order_payment_amount.sql`:

```sql
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_amount DECIMAL(10,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_amount_recorded_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_amount_recorded_by VARCHAR(255);

COMMENT ON COLUMN orders.payment_amount IS
    'Amount actually received for this order. NULL = not recorded (legacy orders).';
```

Variance is **derived**, not stored: `payment_amount − total` (negative = short, positive = overpaid).

## 4. Model

`src/main/java/com/polaroid/model/Order.java` — next to `paymentReference` (`:100-101`):

```java
@Column(name = "payment_amount", precision = 10, scale = 2)
private BigDecimal paymentAmount;

@Column(name = "payment_amount_recorded_at")
private LocalDateTime paymentAmountRecordedAt;

@Column(name = "payment_amount_recorded_by")
private String paymentAmountRecordedBy;
```

## 5. DTOs

`dto/response/OrderResponse.java` — beside `subtotal`/`shipping`/`total`:

```java
private BigDecimal paymentAmount;      // what was received, null = not recorded
private BigDecimal paymentVariance;    // paymentAmount - total, null when unknown
```

`dto/mapper/OrderMapper.java` — in `toDto` (`:66-68`) and `toEntity`:

```java
.paymentAmount(entity.getPaymentAmount())
.paymentVariance(entity.getPaymentAmount() == null || entity.getTotal() == null
        ? null
        : entity.getPaymentAmount().subtract(entity.getTotal()))
```

`dto/request/VerifyPaymentRequest.java`:

```java
@DecimalMin(value = "0.00", message = "Amount received cannot be negative")
private BigDecimal amountReceived;   // required when action = APPROVE and paymentMethod = bank_transfer
private Boolean allowShortPayment;   // explicit override, default false
```

`dto/request/UpdatePaymentStatusRequest.java`:

```java
private BigDecimal amountReceived;   // optional; recorded when paymentStatus = PAID
```

`dto/request/UpdatePaymentProofRequest` — not needed if the proof stays multipart; add a query param instead (see §7).

## 6. Service rules

### 6.1 `OrderService.verifyPayment` (`:421`)

APPROVE branch:

1. `BigDecimal received = request.getAmountReceived();`
2. If `order.getPaymentMethod()` is `bank_transfer` and `received == null` → `BadRequestException("Amount received is required to approve a bank transfer")`.
3. If `received.compareTo(order.getTotal()) < 0` and `!TRUE.equals(request.getAllowShortPayment())` → throw `PriceConflictException` with code `SHORT_PAYMENT`, message `"Received RM<received> is less than the order total RM<total>. Correct the amount or approve with an override."`, and payload `currentTotal = order.getTotal()`.
4. On success: set `paymentAmount`, `paymentAmountRecordedAt = now`, `paymentAmountRecordedBy = adminEmail`.
5. Status-history message:
   `"Payment verified and approved by <admin> — received RM<amount> (prints RM<subtotal> + delivery RM<shipping> = RM<total>)"`,
   plus `" — SHORT by RM<x>, override by <admin>"` when the override was used.

REJECT branch: leave `paymentAmount` untouched (nothing was received).

### 6.2 `OrderService.updatePaymentStatusForAdmin` (`:374`)

When `status == PAID` and `amountReceived` is present, record it exactly as above. Keep the current no-amount behaviour for `REFUNDED`/`FAILED`.

### 6.3 `OrderService.updatePaymentStatus` (`:345`) — gateway + legacy PATCH

Set `paymentAmount = order.getTotal()` when the caller is the ToyyibPay callback (`PaymentService.verifyCallback` already proved `amount == total` at `PaymentService.java:197`), and `paymentAmountRecordedBy = "TOYYIBPAY"`. Do **not** infer an amount for unknown callers.

### 6.4 `PaymentCleanupService` (same PR, small)

`cleanupExpiredPayments` (`:42-56`) sets `CANCELLED` + `cancel_reason` but never calls `addStatusHistory`, which is why `PG77514747E4C3` shows only "Order created" in its timeline. Add a history row (`"Auto-cancelled: payment not received within 24 hours"`, created by `SYSTEM`) and the same for `sendPaymentReminder`.

## 7. Endpoints

### `POST /api/files/payment-proof` (customer, multipart)

New optional query param `amount` (`BigDecimal`):

```
POST /api/files/payment-proof
  file=<screenshot>
  orderId=<uuid>
  reference=<free text, optional>
  amount=11.00            ← new, optional
```

`FileService.uploadPaymentProof` (`:165-180`): when `amount` is present, set `paymentAmount` + `paymentAmountRecordedAt = now` + `paymentAmountRecordedBy = order.getCustomerEmail()`. Do **not** reject a short amount here — the customer may genuinely have underpaid; the seller decides at approval. Do validate `amount >= 0`.

### `POST /api/admin/orders/{orderNumber}/verify-payment`

```json
{ "action": "APPROVE", "amountReceived": 11.00 }
```

Short payment:

```json
{ "action": "APPROVE", "amountReceived": 4.00 }
```
→ `409`

```json
{
  "success": false,
  "code": "SHORT_PAYMENT",
  "error": "Received RM4.00 is less than the order total RM11.00. Correct the amount or approve with an override.",
  "currentTotal": "11.00"
}
```

Override:

```json
{ "action": "APPROVE", "amountReceived": 4.00, "allowShortPayment": true }
```
→ `200`, order `paymentAmount = 4.00`, `paymentVariance = -7.00`, history notes the shortfall.

### `PATCH /api/admin/orders/{id}/payment-status`

```json
{ "paymentStatus": "PAID", "amountReceived": 11.00 }
```

### `GET /api/admin/orders` (unchanged shape, richer rows)

Each order must include:

```json
{
  "orderNumber": "PG77514747E4C3",
  "subtotal": 4.00,
  "shipping": 7.00,
  "total": 11.00,
  "paymentAmount": 11.00,
  "paymentVariance": 0.00,
  "items": [
    { "sizeName": "Polaroid Mini (5.0 x 8.9 cm)", "style": "BORDER",
      "variantLabel": "BORDER", "packageQuantity": 10, "packageCount": 1,
      "quantity": 10, "unitPrice": 4.00, "totalPrice": 4.00 }
  ]
}
```

`paymentAmount` / `paymentVariance` are new; everything else is already returned by `OrderMapper`.

## 8. Frontend contract (already partly done)

`polaroid_glossy_backup` branch `feat/strip-checkout-preparation`:

- `src/app/admin/orders/page.tsx` — item lines now render print size · style · pack size, and the card shows **Subtotal (prints) / Delivery / Total**; a zero delivery prints an amber "Not charged".
- `src/app/profile/orders/[id]/page.tsx` — same split for the customer.

Follow-up once this ships: add a **Received** row bound to `paymentAmount` (fallback `"Not recorded"`) and a red "Short RM x.xx" badge driven by `paymentVariance < 0`.

## 9. Tests

`src/test/java/com/polaroid/service/OrderServicePaymentAmountTest.java`:

1. APPROVE with `amountReceived == total` → `paymentAmount` set, variance 0, history contains the breakdown.
2. APPROVE with `amountReceived < total` and no override → throws, code `SHORT_PAYMENT`, order stays `PENDING`.
3. APPROVE with override → `PAID`, variance negative, history mentions the shortfall.
4. APPROVE bank transfer without `amountReceived` → 400.
5. `PATCH payment-status` to PAID with amount → recorded; without amount → unchanged behaviour.
6. ToyyibPay callback → `paymentAmount == total`, recorded-by `TOYYIBPAY`.
7. Customer proof upload with `amount` → stored, and a short amount does **not** block the upload.
8. Legacy order (`paymentAmount == null`) → `OrderResponse.paymentVariance == null`, mapping does not throw.

Run:

```bash
MSYS_NO_PATHCONV=1 cmd.exe /c "C:\Users\User\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd -o -B test"
```

## 10. Acceptance criteria

- [ ] `V31` applies cleanly on the existing Supabase database (`orders.payment_amount` nullable).
- [ ] Approving a short bank transfer is impossible without `allowShortPayment: true`.
- [ ] `GET /api/admin/orders` carries `subtotal`, `shipping`, `total`, `paymentAmount`, `paymentVariance`, and per-item `sizeName`/`style`/`variantLabel`/`packageQuantity`.
- [ ] Admin dashboard shows four lines: Subtotal (prints), Delivery, Total, Received.
- [ ] `PG77514747E4C3` and every older order still render (null amount → "Not recorded").
- [ ] Auto-cancel and payment reminder now appear in the order timeline.

## 11. Out of scope

- Automatic bank-statement reconciliation / upload import.
- Refunds and partial refunds against `paymentAmount`.
- Changing the delivery tariff (currently flat RM7 peninsular / RM11 Sabah, Sarawak, Labuan in `OrderService.shippingCost:618-633`).

## 12. Notes for the frontend after merge

1. `NEXT_PUBLIC_BACKEND_API_BASE` unchanged — the Next proxy (`/api/admin/orders`) already forwards the token.
2. No Prisma work: per `AGENTS.md` the frontend never reads the database; everything arrives through the proxy.
3. Keep the delivery line honest: render "Not charged" whenever `shipping` is `0`/null so any order that slipped through without postage stays visible.
