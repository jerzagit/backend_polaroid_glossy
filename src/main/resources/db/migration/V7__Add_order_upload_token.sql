ALTER TABLE orders
    ADD COLUMN upload_token_hash VARCHAR(255),
    ADD COLUMN upload_token_expires_at TIMESTAMPTZ;

CREATE INDEX idx_orders_upload_token_expires_at ON orders(upload_token_expires_at);
