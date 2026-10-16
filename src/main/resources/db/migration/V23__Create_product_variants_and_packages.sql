-- Size -> Style -> Package catalog.
-- Each print size (product) gets up to two style variants (FULL / BORDER),
-- and each variant owns its own package quantities with whole-package prices.
-- Prices are DECIMAL(10,2) money values (never floats).

CREATE TABLE product_variants (
    id VARCHAR(64) PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL REFERENCES print_sizes(id),
    style VARCHAR(16) NOT NULL,
    label VARCHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT chk_product_variants_style CHECK (style IN ('FULL', 'BORDER'))
);

CREATE TABLE product_packages (
    id VARCHAR(64) PRIMARY KEY,
    variant_id VARCHAR(64) NOT NULL REFERENCES product_variants(id),
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'MYR',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT chk_product_packages_quantity CHECK (quantity > 0),
    CONSTRAINT chk_product_packages_price CHECK (price > 0),
    CONSTRAINT uq_product_packages_variant_quantity UNIQUE (variant_id, quantity)
);

CREATE INDEX idx_product_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_product_packages_variant_id ON product_packages(variant_id);
