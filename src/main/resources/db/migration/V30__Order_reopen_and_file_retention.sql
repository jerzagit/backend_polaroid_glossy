-- Reopen support and delayed object-storage retention.
-- Order rows and metadata are kept forever; only R2/S3 objects are deleted.

ALTER TABLE orders
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopened_by VARCHAR(255),
    ADD COLUMN reopen_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN files_delete_after TIMESTAMPTZ,
    ADD COLUMN files_deleted_at TIMESTAMPTZ,
    ADD COLUMN files_delete_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN files_delete_error TEXT;

-- Cancelled before shipping: 5 days. Cancelled after shipping/refunded: 14 days.
UPDATE orders
SET files_delete_after = COALESCE(cancelled_at, draft_expired_at, updated_at, created_at)
    + CASE
        WHEN shipped_at IS NOT NULL OR delivered_at IS NOT NULL THEN INTERVAL '14 days'
        ELSE INTERVAL '5 days'
      END
WHERE status IN ('CANCELLED', 'EXPIRED', 'REFUNDED')
  AND files_delete_after IS NULL;

-- Completed / in-flight fulfilment orders: 14 days from delivery, otherwise
-- from the best available milestone.
UPDATE orders
SET files_delete_after = COALESCE(delivered_at, shipped_at, updated_at, created_at) + INTERVAL '14 days'
WHERE status IN ('POSTED', 'ON_DELIVERY', 'DELIVERED')
  AND files_delete_after IS NULL;

CREATE INDEX idx_orders_files_delete_due
    ON orders (files_delete_after)
    WHERE files_delete_after IS NOT NULL AND files_deleted_at IS NULL;
