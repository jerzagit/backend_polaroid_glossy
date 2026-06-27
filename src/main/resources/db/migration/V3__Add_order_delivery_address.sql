ALTER TABLE orders
    ADD COLUMN customer_house_unit_no VARCHAR(100),
    ADD COLUMN customer_address_line_1 VARCHAR(255),
    ADD COLUMN customer_address_line_2 VARCHAR(255),
    ADD COLUMN customer_postcode VARCHAR(5),
    ADD COLUMN customer_country VARCHAR(100) DEFAULT 'Malaysia';

UPDATE orders
SET customer_country = 'Malaysia'
WHERE customer_country IS NULL;
