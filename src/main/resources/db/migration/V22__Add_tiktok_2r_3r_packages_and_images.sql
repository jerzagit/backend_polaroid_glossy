-- TikTok Shop Malaysia prices observed 2026-10-14 (MYT).
-- 2R: https://shop.tiktok.com/my/pdp/1730804048048588502
-- 3R: https://shop.tiktok.com/my/pdp/1730808529421436630
-- Print-only packages; album bundles excluded. No crossed-out prices were shown,
-- so regular_price and discounted_price both use the displayed package total (MYR).

UPDATE print_sizes SET price = 2.20 WHERE id = '2r-no-border';
UPDATE product_meta SET images = '["https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-full.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-cover.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-details.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/paper-details.webp"]' WHERE id = '2r-no-border';

UPDATE print_sizes SET price = 2.00 WHERE id = '2r-border';
UPDATE product_meta SET images = '["https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-border.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-cover.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-details.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/paper-details.webp"]' WHERE id = '2r-border';

UPDATE print_sizes SET price = 2.50 WHERE id = '3r-no-border';
UPDATE product_meta SET images = '["https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-variant-1.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-cover.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-details.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-details-2.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/paper-details.webp"]' WHERE id = '3r-no-border';

INSERT INTO print_size_pricing_tiers (id, size_id, quantity, regular_price, discounted_price, sort_order) VALUES
('tier-2r-no-border-1', '2r-no-border', 1, 2.20, 2.20, 1),
('tier-2r-no-border-10', '2r-no-border', 10, 7.00, 7.00, 2),
('tier-2r-no-border-20', '2r-no-border', 20, 14.00, 14.00, 3),
('tier-2r-no-border-50', '2r-no-border', 50, 35.00, 35.00, 4),
('tier-2r-no-border-100', '2r-no-border', 100, 60.00, 60.00, 5),
('tier-2r-border-1', '2r-border', 1, 2.00, 2.00, 1),
('tier-2r-border-10', '2r-border', 10, 6.50, 6.50, 2),
('tier-2r-border-20', '2r-border', 20, 13.00, 13.00, 3),
('tier-2r-border-50', '2r-border', 50, 32.00, 32.00, 4),
('tier-2r-border-100', '2r-border', 100, 60.00, 60.00, 5),
('tier-3r-no-border-1', '3r-no-border', 1, 2.50, 2.50, 1),
('tier-3r-no-border-10', '3r-no-border', 10, 8.00, 8.00, 2),
('tier-3r-no-border-20', '3r-no-border', 20, 16.00, 16.00, 3),
('tier-3r-no-border-50', '3r-no-border', 50, 40.00, 40.00, 4),
('tier-3r-no-border-100', '3r-no-border', 100, 80.00, 80.00, 5)
ON CONFLICT (id) DO UPDATE SET
    quantity = EXCLUDED.quantity,
    regular_price = EXCLUDED.regular_price,
    discounted_price = EXCLUDED.discounted_price,
    sort_order = EXCLUDED.sort_order;

