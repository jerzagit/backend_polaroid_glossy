ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS expected_image_count INT;

UPDATE order_items
SET expected_image_count = quantity
WHERE expected_image_count IS NULL;
