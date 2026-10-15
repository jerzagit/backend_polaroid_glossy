-- Purchase-time snapshot of the selected size, style, and package.
-- These columns intentionally have NO foreign keys: later catalog edits
-- (price changes, disabling packages) must never alter historical orders.

ALTER TABLE order_items
    ADD COLUMN variant_id VARCHAR(64),
    ADD COLUMN variant_label VARCHAR(64),
    ADD COLUMN style VARCHAR(16),
    ADD COLUMN package_id VARCHAR(64),
    ADD COLUMN package_quantity INT,
    ADD COLUMN package_price DECIMAL(10,2),
    ADD COLUMN package_count INT;
