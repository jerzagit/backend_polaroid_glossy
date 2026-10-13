CREATE TABLE product_meta (
    id VARCHAR(50) PRIMARY KEY,
    tag VARCHAR(50),
    accent_color VARCHAR(20),
    images TEXT,
    features TEXT,
    tiktok_videos TEXT,
    short_description TEXT,
    full_description TEXT,
    rating DECIMAL(3,2),
    review_count INT DEFAULT 0,
    popular BOOLEAN DEFAULT false
);

CREATE TABLE print_size_pricing_tiers (
    id VARCHAR(50) PRIMARY KEY,
    size_id VARCHAR(50) NOT NULL REFERENCES print_sizes(id),
    quantity INT NOT NULL,
    regular_price DECIMAL(10,2) NOT NULL,
    discounted_price DECIMAL(10,2) NOT NULL,
    sort_order INT DEFAULT 0
);

CREATE INDEX idx_product_meta_popular ON product_meta(popular);
CREATE INDEX idx_pricing_tiers_size_id ON print_size_pricing_tiers(size_id);
