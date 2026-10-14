-- V20 references these products before V21 can create them.
-- Keep existing catalog records and applied migration checksums unchanged.
-- Match the catalog model: long product IDs and strips without fixed dimensions.
ALTER TABLE print_sizes ALTER COLUMN id TYPE VARCHAR(255);
ALTER TABLE print_sizes ALTER COLUMN width DROP NOT NULL;
ALTER TABLE print_sizes ALTER COLUMN height DROP NOT NULL;

INSERT INTO print_sizes (id, name, display_name, width, height, price, description, is_active, tag) VALUES
('ic-border', 'IC Border', 'IC Border (5.5 x 8.9 cm)', 5.5, 8.9, 2.50, 'Premium instant photo with decorative border', true, 'POLAROID'),
('ic', 'IC', 'IC (5.5 x 8.9 cm)', 5.5, 8.9, 2.00, 'Classic instant photo, no border', true, 'POLAROID'),
('polaroid-mini', 'Polaroid Mini', 'Polaroid Mini (5.0 x 8.9 cm)', 5.0, 8.9, 1.50, 'Compact instant photo for on-the-go', true, 'MINI'),
('strip-3', 'Strip 3', 'Strip 3 (3-photo strip)', NULL, NULL, 1.80, '3-photo strip for creative layouts', true, 'STRIP'),
('strip-4', 'Strip 4', 'Strip 4 (4-photo strip)', NULL, NULL, 1.80, '4-photo strip for creative layouts', true, 'STRIP'),
('4r', '4R', '4R (4 x 6 inches)', 4.0, 6.0, 1.00, 'Most popular - Classic polaroid style', true, 'BESTSELLER'),
('5r', '5R', '5R (5 x 7 inches)', 5.0, 7.0, 1.50, 'Large format instant photo', true, '5R'),
('6r', '6R', '6R (6 x 8 inches)', 6.0, 8.0, 2.00, 'Extra large instant photo', true, '6R')
ON CONFLICT (id) DO NOTHING;
