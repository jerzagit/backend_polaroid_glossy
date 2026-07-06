ALTER TABLE orders ADD COLUMN payment_proof_url VARCHAR(500);
ALTER TABLE orders ADD COLUMN payment_reference VARCHAR(100);

CREATE INDEX idx_orders_payment_proof ON orders(payment_proof_url) WHERE payment_proof_url IS NOT NULL;
