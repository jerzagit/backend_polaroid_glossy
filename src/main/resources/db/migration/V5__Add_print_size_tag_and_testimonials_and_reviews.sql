ALTER TABLE print_sizes
    ADD COLUMN IF NOT EXISTS tag VARCHAR(255);

CREATE TABLE IF NOT EXISTS testimonials (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    location    VARCHAR(255) NOT NULL,
    text        TEXT NOT NULL,
    print_type  VARCHAR(100) NOT NULL,
    image_url   VARCHAR(500),
    text_my     TEXT,
    print_type_my VARCHAR(255),
    rating      INTEGER DEFAULT 5,
    sort_order  INTEGER DEFAULT 0,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS reviews (
    id          SERIAL PRIMARY KEY,
    user_id     UUID,
    order_id    UUID,
    size_id     VARCHAR(255),
    rating      INTEGER NOT NULL,
    title       VARCHAR(255) NOT NULL,
    comment     TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);
