INSERT INTO print_sizes (id, name, display_name, width, height, price, description, is_active, tag) VALUES
('ic-border', 'IC Border', 'IC Border (5.5 x 8.9 cm)', 5.5, 8.9, 2.50, 'Premium instant photo with decorative border', true, 'POLAROID'),
('ic', 'IC', 'IC (5.5 x 8.9 cm)', 5.5, 8.9, 2.00, 'Classic instant photo, no border', true, 'POLAROID'),
('polaroid-mini', 'Polaroid Mini', 'Polaroid Mini (5.0 x 8.9 cm)', 5.0, 8.9, 1.50, 'Compact instant photo for on-the-go', true, 'MINI'),
('strip-3', 'Strip 3', 'Strip 3 (3-photo strip)', NULL, NULL, 1.80, '3-photo strip for creative layouts', true, 'STRIP'),
('strip-4', 'Strip 4', 'Strip 4 (4-photo strip)', NULL, NULL, 1.80, '4-photo strip for creative layouts', true, 'STRIP')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    width = EXCLUDED.width,
    height = EXCLUDED.height,
    price = EXCLUDED.price,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    tag = EXCLUDED.tag;

UPDATE print_sizes SET price = 1.00, display_name = '4R (4 x 6 inches)', description = 'Most popular - Classic polaroid style' WHERE id = '4r';
UPDATE print_sizes SET price = 1.50, display_name = '5R (5 x 7 inches)', description = 'Large format instant photo' WHERE id = '5r';
UPDATE print_sizes SET price = 2.00, display_name = '6R (6 x 8 inches)', description = 'Extra large instant photo' WHERE id = '6r';
