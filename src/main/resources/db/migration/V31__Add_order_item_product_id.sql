-- Preserve the catalog identity independently of future catalog changes.
-- Legacy orders remain nullable: do not guess historical IDs from today's catalog.
ALTER TABLE order_items ADD COLUMN product_id BIGINT;
