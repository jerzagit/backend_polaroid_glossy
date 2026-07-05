UPDATE orders
SET status = 'POSTED'
WHERE status = 'SHIPPED';

UPDATE order_status_history
SET status = 'POSTED'
WHERE status = 'SHIPPED';

UPDATE order_items
SET size_id = lower(size_id)
WHERE size_id <> lower(size_id);

UPDATE print_sizes
SET id = lower(id)
WHERE id <> lower(id);
