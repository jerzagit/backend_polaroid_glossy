-- Seed the canonical product catalog: one product per print size, Full/Border
-- styles for IC / 2R / 3R, Full-only for every other size, and the packages
-- beneath each style.
--
-- Price sources:
--   * IC                -> verified TikTok Shop MY listing (2026-10-16, V24).
--   * 2R / 3R           -> observed TikTok Shop MY listings (2026-10-14, V22).
--   * 4R / 5R / 6R / Polaroid Mini / Strips -> "Current Website Pricing"
--     (PLAN-backend-product-api.md).
--   * A4                -> derived from its existing per-print price so the
--                          total is unchanged from today (seller reviews later).
-- All prices are whole-package totals in MYR, never per-print and never floats.
-- The seller dashboard can add / edit packages and styles at any time.

INSERT INTO products (code, name, display_name, width, height, base_price, description, tag, is_active, sort_order) VALUES
 ('ic', 'IC', 'IC (5.5 x 8.9 cm)', 5.5, 8.9, 2.00, 'Classic instant photo, no border', 'POLAROID', TRUE, 1),
 ('2r', '2R', '2R (6.3 x 8.9 cm)', 6.3, 8.9, 2.20, 'Full-colour card without white border', '2R', TRUE, 2),
 ('3r', '3R', '3R (8.9 x 12.7 cm)', 8.9, 12.7, 2.50, 'Standard photo without border', '3R', TRUE, 3),
 ('4r', '4R', '4R (4 x 6 inches)', 4.0, 6.0, 1.00, 'Most popular - Classic polaroid style', 'BESTSELLER', TRUE, 4),
 ('5r', '5R', '5R (5 x 7 inches)', 5.0, 7.0, 1.50, 'Large format instant photo', '5R', TRUE, 5),
 ('6r', '6R', '6R (6 x 8 inches)', 6.0, 8.0, 2.00, 'Extra large instant photo', '6R', TRUE, 6),
 ('polaroid-mini', 'Polaroid Mini', 'Polaroid Mini (5.0 x 8.9 cm)', 5.0, 8.9, 1.50, 'Compact instant photo for on-the-go', 'MINI', TRUE, 7),
 ('strip-3', 'Strip 3', 'Strip 3 (3-photo strip)', NULL, NULL, 1.80, '3-photo strip for creative layouts', 'STRIP', TRUE, 8),
 ('strip-4', 'Strip 4', 'Strip 4 (4-photo strip)', NULL, NULL, 1.80, '4-photo strip for creative layouts', 'STRIP', TRUE, 9),
 ('a4', 'A4', 'A4 (8.3 x 11.7 inches)', 8.3, 11.7, 3.50, 'Poster size - Perfect for displays', 'PREMIUM', TRUE, 10);

-- Every size offers Full printing; IC / 2R / 3R also offer the Border style.
INSERT INTO product_variants (product_id, style, label, is_active, sort_order)
SELECT id, 'FULL', 'Full', TRUE, 1 FROM products;

INSERT INTO product_variants (product_id, style, label, is_active, sort_order)
SELECT p.id, 'BORDER', 'Border (Polaroid)', TRUE, 2
FROM products p
WHERE p.code IN ('ic', '2r', '3r');

INSERT INTO product_packages (variant_id, quantity, price, currency, is_active, sort_order)
SELECT v.id, d.quantity, d.price, 'MYR', TRUE, d.sort_order
FROM (VALUES
    -- IC (verified 2026-10-16)
    ('ic', 'FULL',   10, 6.00, 1),
    ('ic', 'FULL',   20, 12.00, 2),
    ('ic', 'FULL',   50, 30.00, 3),
    ('ic', 'FULL',  100, 55.00, 4),
    ('ic', 'BORDER', 10, 5.00, 1),
    ('ic', 'BORDER', 20, 10.00, 2),
    ('ic', 'BORDER', 50, 25.00, 3),
    ('ic', 'BORDER',100, 45.00, 4),
    -- 2R (TikTok observed 2026-10-14)
    ('2r', 'FULL',    1, 2.20, 1),
    ('2r', 'FULL',   10, 7.00, 2),
    ('2r', 'FULL',   20, 14.00, 3),
    ('2r', 'FULL',   50, 35.00, 4),
    ('2r', 'FULL',  100, 60.00, 5),
    ('2r', 'BORDER',  1, 2.00, 1),
    ('2r', 'BORDER', 10, 6.50, 2),
    ('2r', 'BORDER', 20, 13.00, 3),
    ('2r', 'BORDER', 50, 32.00, 4),
    ('2r', 'BORDER',100, 60.00, 5),
    -- 3R (Full observed 2026-10-14; Border from current website pricing)
    ('3r', 'FULL',    1, 2.50, 1),
    ('3r', 'FULL',   10, 8.00, 2),
    ('3r', 'FULL',   20, 16.00, 3),
    ('3r', 'FULL',   50, 40.00, 4),
    ('3r', 'FULL',  100, 80.00, 5),
    ('3r', 'BORDER',  1, 2.40, 1),
    ('3r', 'BORDER', 10, 7.00, 2),
    ('3r', 'BORDER', 20, 14.00, 3),
    ('3r', 'BORDER', 50, 35.00, 4),
    ('3r', 'BORDER',100, 70.00, 5),
    -- 4R
    ('4r', 'FULL',    1, 2.50, 1),
    ('4r', 'FULL',   10, 10.00, 2),
    ('4r', 'FULL',   20, 20.00, 3),
    ('4r', 'FULL',   50, 50.00, 4),
    ('4r', 'FULL',  100, 100.00, 5),
    -- 5R
    ('5r', 'FULL',    1, 4.00, 1),
    ('5r', 'FULL',   10, 20.00, 2),
    -- 6R
    ('6r', 'FULL',    1, 5.00, 1),
    ('6r', 'FULL',   10, 25.00, 2),
    -- Polaroid Mini
    ('polaroid-mini', 'FULL',   1, 1.50, 1),
    ('polaroid-mini', 'FULL',   5, 4.00, 2),
    ('polaroid-mini', 'FULL',  10, 5.00, 3),
    ('polaroid-mini', 'FULL',  20, 8.00, 4),
    ('polaroid-mini', 'FULL',  50, 15.00, 5),
    ('polaroid-mini', 'FULL', 100, 30.00, 6),
    ('polaroid-mini', 'FULL', 250, 75.00, 7),
    -- Strips
    ('strip-3', 'FULL', 1, 1.80, 1),
    ('strip-4', 'FULL', 1, 1.80, 1),
    -- A4 (derived from the existing per-print price of 3.50)
    ('a4', 'FULL',   10, 35.00, 1),
    ('a4', 'FULL',   25, 87.50, 2),
    ('a4', 'FULL',   50, 175.00, 3),
    ('a4', 'FULL',  100, 350.00, 4)
) AS d(code, style, quantity, price, sort_order)
JOIN products p ON p.code = d.code
JOIN product_variants v ON v.product_id = p.id AND v.style = d.style;

-- Old product links keep working: each legacy id resolves to a style of the
-- merged product (ic-border -> IC Border, 2r-no-border -> 2R Full, ...).
INSERT INTO product_aliases (legacy_code, product_id, style, created_at)
SELECT d.legacy_code, p.id, d.style, CURRENT_TIMESTAMP
FROM (VALUES
    ('ic-border',    'ic', 'BORDER'),
    ('2r-no-border', '2r', 'FULL'),
    ('2r-border',    '2r', 'BORDER'),
    ('3r-no-border', '3r', 'FULL'),
    ('3r-border',    '3r', 'BORDER')
) AS d(legacy_code, code, style)
JOIN products p ON p.code = d.code;

-- Marketing meta for the newly canonical 2R / 3R products (copied from the
-- former no-border rows, then given a combined Full + Border image set).
INSERT INTO product_meta (id, tag, accent_color, images, features, tiktok_videos, short_description, full_description, rating, review_count, popular)
SELECT '2r', tag, accent_color, images, features, tiktok_videos, short_description, full_description, rating, review_count, popular
FROM product_meta WHERE id = '2r-no-border';

INSERT INTO product_meta (id, tag, accent_color, images, features, tiktok_videos, short_description, full_description, rating, review_count, popular)
SELECT '3r', tag, accent_color, images, features, tiktok_videos, short_description, full_description, rating, review_count, popular
FROM product_meta WHERE id = '3r-no-border';

UPDATE product_meta SET images =
    '["https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-full.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-border.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-cover.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/2r-details.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/paper-details.webp"]'
WHERE id = '2r';

UPDATE product_meta SET images =
    '["https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-variant-1.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-cover.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-details.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/3r-details-2.webp","https://polaroid-glossy-backend.fly.dev/images/products/tiktok/paper-details.webp"]'
WHERE id = '3r';

-- Soft-retire the duplicate per-style rows (never deleted: historical orders
-- and the legacy checkout path still reference them) and drop their now
-- superseded per-print pricing tiers.
UPDATE print_sizes SET is_active = FALSE
WHERE id IN ('ic-border', '2r-no-border', '2r-border', '3r-no-border', '3r-border');

DELETE FROM print_size_pricing_tiers
WHERE size_id IN ('2r-no-border', '2r-border', '3r-no-border', '3r-border');
