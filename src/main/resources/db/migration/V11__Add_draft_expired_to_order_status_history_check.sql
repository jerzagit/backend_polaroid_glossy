ALTER TABLE order_status_history DROP CONSTRAINT IF EXISTS order_status_history_status_check;
ALTER TABLE order_status_history ADD CONSTRAINT order_status_history_status_check CHECK (status IN ('DRAFT', 'PENDING', 'PROCESSING', 'POSTED', 'ON_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUNDED', 'EXPIRED'));
