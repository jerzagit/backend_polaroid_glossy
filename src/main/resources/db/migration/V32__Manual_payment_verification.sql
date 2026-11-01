ALTER TABLE orders ADD COLUMN payment_proof_source VARCHAR(255);
ALTER TABLE orders ADD COLUMN payment_review_status VARCHAR(255);
ALTER TABLE orders ADD COLUMN payment_verified_by VARCHAR(255);
ALTER TABLE orders ADD COLUMN payment_verified_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN payment_amount_received DECIMAL(10,2);
ALTER TABLE orders ADD COLUMN payment_review_history TEXT;
UPDATE orders SET payment_proof_source = 'SYSTEM_UPLOAD', payment_review_status = 'AWAITING_VERIFICATION'
WHERE payment_proof_url IS NOT NULL AND payment_status <> 'PAID';
