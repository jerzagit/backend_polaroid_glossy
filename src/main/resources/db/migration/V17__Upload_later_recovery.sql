ALTER TABLE orders ADD COLUMN upload_mode VARCHAR(16) DEFAULT 'now';
ALTER TABLE orders ADD COLUMN checkout_key VARCHAR(64);
ALTER TABLE orders ADD COLUMN payment_reminder_sent_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN upload_reminder_sent_at TIMESTAMP;
CREATE UNIQUE INDEX orders_user_checkout_key ON orders(user_id, checkout_key) WHERE checkout_key IS NOT NULL;
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
