ALTER TABLE order_items ADD COLUMN strip_design TEXT;

CREATE TABLE strip_checkout_requests (
    request_key VARCHAR(64) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID REFERENCES orders(id),
    encrypted_upload_token TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
