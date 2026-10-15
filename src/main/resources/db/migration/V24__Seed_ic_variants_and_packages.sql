-- IC (5.5 x 8.9 cm) Size -> Style -> Package catalog.
-- Verified against the TikTok Shop MY listing (observed 2026-10-16):
-- https://shop.tiktok.com/my/pdp/polaroid-harga-kilang-glossy-waterproof-5-5x8-9-cm/1734428964686628566
-- Prices are whole-package totals in MYR (not per print).
-- These prices apply to IC only; other sizes are seeded from TikTok listings
-- or seller-entered prices once verified.

INSERT INTO product_variants (id, product_id, style, label, is_active, sort_order) VALUES
('ic-full', 'ic', 'FULL', 'Full', TRUE, 1),
('ic-border', 'ic', 'BORDER', 'Border (Polaroid)', TRUE, 2);

INSERT INTO product_packages (id, variant_id, quantity, price, currency, is_active, sort_order) VALUES
('ic-full-10', 'ic-full', 10, 6.00, 'MYR', TRUE, 1),
('ic-full-20', 'ic-full', 20, 12.00, 'MYR', TRUE, 2),
('ic-full-50', 'ic-full', 50, 30.00, 'MYR', TRUE, 3),
('ic-full-100', 'ic-full', 100, 55.00, 'MYR', TRUE, 4),
('ic-border-10', 'ic-border', 10, 5.00, 'MYR', TRUE, 1),
('ic-border-20', 'ic-border', 20, 10.00, 'MYR', TRUE, 2),
('ic-border-50', 'ic-border', 50, 25.00, 'MYR', TRUE, 3),
('ic-border-100', 'ic-border', 100, 45.00, 'MYR', TRUE, 4);

-- Retire the superseded per-print pricing tiers for IC and IC Border,
-- including the incorrect 25 pcs tier. The variant packages above are now
-- the source of truth for IC pricing. No packages are copied between sizes
-- or styles.
DELETE FROM print_size_pricing_tiers WHERE size_id IN ('ic', 'ic-border');
