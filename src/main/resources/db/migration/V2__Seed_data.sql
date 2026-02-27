-- Polaroid Glossy Database Migration V2
-- Seed Data for all tables

-- =============================================
-- USERS SEED DATA
-- =============================================

-- Admin user (password: admin123)
-- BCrypt hash for 'admin123'
INSERT INTO users (id, email, password_hash, name, phone, role, is_active, created_at, updated_at) VALUES
('a0000000-0000-0000-0000-000000000001', 'admin@polaroid.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Admin User', '+60123456789', 'ADMIN', true, NOW(), NOW());

-- Sample customers
INSERT INTO users (id, email, password_hash, name, phone, role, is_active, created_at, updated_at) VALUES
('b0000000-0000-0000-0000-000000000001', 'john.doe@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'John Doe', '+60111111111', 'CUSTOMER', true, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
('b0000000-0000-0000-0000-000000000002', 'jane.smith@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Jane Smith', '+60122222222', 'CUSTOMER', true, NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
('b0000000-0000-0000-0000-000000000003', 'alex.tan@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Alex Tan', '+60133333333', 'CUSTOMER', true, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
('b0000000-0000-0000-0000-000000000004', 'siti.aminah@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Siti Aminah', '+60144444444', 'AFFILIATE', true, NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days'),
('b0000000-0000-0000-0000-000000000005', 'mike.chen@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Mike Chen', '+60155555555', 'CUSTOMER', true, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days');

-- =============================================
-- ORDERS SEED DATA
-- =============================================

-- Order 1 - Completed
INSERT INTO orders (id, order_number, user_id, affiliate_id, customer_name, customer_email, customer_phone, customer_state, status, payment_status, payment_method, toyyibpay_ref, subtotal, shipping, total, paid_at, shipped_at, delivered_at, created_at, updated_at) VALUES
('c0000000-0000-0000-0000-000000000001', 'ORD-2026-0001', 'b0000000-0000-0000-0000-000000000001', NULL, 'John Doe', 'john.doe@example.com', '+60111111111', 'W', 'DELIVERED', 'PAID', 'TOYYIBPAY', 'TXN001', 15.00, 5.00, 20.00, NOW() - INTERVAL '25 days', NOW() - INTERVAL '22 days', NOW() - INTERVAL '20 days', NOW() - INTERVAL '25 days', NOW() - INTERVAL '20 days');

-- Order 2 - Shipped
INSERT INTO orders (id, order_number, user_id, affiliate_id, customer_name, customer_email, customer_phone, customer_state, status, payment_status, payment_method, toyyibpay_ref, subtotal, shipping, total, paid_at, shipped_at, created_at, updated_at) VALUES
('c0000000-0000-0000-0000-000000000002', 'ORD-2026-0002', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000004', 'Jane Smith', 'jane.smith@example.com', '+60122222222', 'KL', 'SHIPPED', 'PAID', 'TOYYIBPAY', 'TXN002', 22.50, 5.00, 27.50, NOW() - INTERVAL '5 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '3 days');

-- Order 3 - Pending Payment
INSERT INTO orders (id, order_number, user_id, affiliate_id, customer_name, customer_email, customer_phone, customer_state, status, payment_status, payment_method, toyyibpay_ref, subtotal, shipping, total, created_at, updated_at) VALUES
('c0000000-0000-0000-0000-000000000003', 'ORD-2026-0003', 'b0000000-0000-0000-0000-000000000003', NULL, 'Alex Tan', 'alex.tan@example.com', '+60133333333', 'SEL', 'PENDING', 'PENDING', 'TOYYIBPAY', 'TXN003', 10.00, 5.00, 15.00, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- Order 4 - Processing
INSERT INTO orders (id, order_number, user_id, affiliate_id, customer_name, customer_email, customer_phone, customer_state, status, payment_status, payment_method, toyyibpay_ref, subtotal, shipping, total, paid_at, created_at, updated_at) VALUES
('c0000000-0000-0000-0000-000000000004', 'ORD-2026-0004', 'b0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000004', 'Mike Chen', 'mike.chen@example.com', '+60155555555', 'JHR', 'PROCESSING', 'PAID', 'TOYYIBPAY', 'TXN004', 18.00, 5.00, 23.00, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day');

-- Order 5 - Cancelled
INSERT INTO orders (id, order_number, user_id, affiliate_id, customer_name, customer_email, customer_phone, customer_state, status, payment_status, payment_method, toyyibpay_ref, subtotal, shipping, total, paid_at, cancelled_at, cancel_reason, created_at, updated_at) VALUES
('c0000000-0000-0000-0000-000000000005', 'ORD-2026-0005', 'b0000000-0000-0000-0000-000000000001', NULL, 'John Doe', 'john.doe@example.com', '+60111111111', 'W', 'CANCELLED', 'REFUNDED', 'TOYYIBPAY', 'TXN005', 8.00, 5.00, 13.00, NOW() - INTERVAL '10 days', NOW() - INTERVAL '8 days', 'Customer requested cancellation', NOW() - INTERVAL '10 days', NOW() - INTERVAL '8 days');

-- =============================================
-- ORDER ITEMS SEED DATA
-- =============================================

-- Order 1 items
INSERT INTO order_items (id, order_id, size_id, size_name, quantity, unit_price, total_price, images, custom_texts, s3_keys, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', '4R', '4R', 10, 1.00, 10.00, '["image1.jpg", "image2.jpg"]', '[]', '["uploads/order1/img1.jpg", "uploads/order1/img2.jpg"]', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
('d0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', '3R', '3R', 5, 0.75, 3.75, '["image3.jpg"]', '[]', '["uploads/order1/img3.jpg"]', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
('d0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'A4', 'A4', 1, 3.50, 3.50, '["image4.jpg"]', '[]', '["uploads/order1/img4.jpg"]', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days');

-- Order 2 items
INSERT INTO order_items (id, order_id, size_id, size_name, quantity, unit_price, total_price, images, custom_texts, s3_keys, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002', '4R', '4R', 15, 1.00, 15.00, '["img1.jpg", "img2.jpg", "img3.jpg"]', '[]', '["uploads/order2/img1.jpg", "uploads/order2/img2.jpg", "uploads/order2/img3.jpg"]', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
('d0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000002', '2R', '2R', 10, 0.50, 5.00, '["img4.jpg", "img5.jpg"]', '[]', '["uploads/order2/img4.jpg", "uploads/order2/img5.jpg"]', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days');

-- Order 3 items (Pending)
INSERT INTO order_items (id, order_id, size_id, size_name, quantity, unit_price, total_price, images, custom_texts, s3_keys, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000003', '3R', '3R', 10, 0.75, 7.50, '["photo1.jpg", "photo2.jpg"]', '[]', '["uploads/order3/photo1.jpg", "uploads/order3/photo2.jpg"]', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('d0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000003', 'A4', 'A4', 1, 3.50, 3.50, '["photo3.jpg"]', '[]', '["uploads/order3/photo3.jpg"]', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- Order 4 items
INSERT INTO order_items (id, order_id, size_id, size_name, quantity, unit_price, total_price, images, custom_texts, s3_keys, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000004', '4R', '4R', 12, 1.00, 12.00, '["pic1.jpg", "pic2.jpg", "pic3.jpg"]', '[]', '["uploads/order4/pic1.jpg", "uploads/order4/pic2.jpg", "uploads/order4/pic3.jpg"]', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('d0000000-0000-0000-0000-000000000009', 'c0000000-0000-0000-0000-000000000004', '3R', '3R', 8, 0.75, 6.00, '["pic4.jpg"]', '[]', '["uploads/order4/pic4.jpg"]', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

-- Order 5 items (Cancelled)
INSERT INTO order_items (id, order_id, size_id, size_name, quantity, unit_price, total_price, images, custom_texts, s3_keys, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000010', 'c0000000-0000-0000-0000-000000000005', '2R', '2R', 16, 0.50, 8.00, '["cancel1.jpg"]', '[]', '["uploads/order5/cancel1.jpg"]', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days');

-- =============================================
-- ORDER STATUS HISTORY SEED DATA
-- =============================================

-- Order 1 status history (Completed full cycle)
INSERT INTO order_status_history (id, order_id, status, message, created_at, updated_at) VALUES
('e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'PENDING', 'Order placed successfully', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
('e0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'PROCESSING', 'Payment confirmed, order being processed', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
('e0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'PROCESSING', 'Printing in progress', NOW() - INTERVAL '24 days', NOW() - INTERVAL '24 days'),
('e0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000001', 'SHIPPED', 'Order shipped via Pos Laju', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days'),
('e0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000001', 'DELIVERED', 'Order delivered successfully', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days');

-- Order 2 status history
INSERT INTO order_status_history (id, order_id, status, message, created_at, updated_at) VALUES
('e0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000002', 'PENDING', 'Order placed successfully', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
('e0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000002', 'PROCESSING', 'Payment confirmed, order being processed', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
('e0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000002', 'SHIPPED', 'Order shipped via Pos Laju, Tracking: PL123456789', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days');

-- Order 3 status history (Pending)
INSERT INTO order_status_history (id, order_id, status, message, created_at, updated_at) VALUES
('e0000000-0000-0000-0000-000000000009', 'c0000000-0000-0000-0000-000000000003', 'PENDING', 'Order placed successfully, awaiting payment', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- Order 4 status history
INSERT INTO order_status_history (id, order_id, status, message, created_at, updated_at) VALUES
('e0000000-0000-0000-0000-000000000010', 'c0000000-0000-0000-0000-000000000004', 'PENDING', 'Order placed successfully', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('e0000000-0000-0000-0000-000000000011', 'c0000000-0000-0000-0000-000000000004', 'PROCESSING', 'Payment confirmed, order being processed', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('e0000000-0000-0000-0000-000000000012', 'c0000000-0000-0000-0000-000000000004', 'PROCESSING', 'Printing in progress', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- Order 5 status history (Cancelled)
INSERT INTO order_status_history (id, order_id, status, message, created_at, updated_at) VALUES
('e0000000-0000-0000-0000-000000000013', 'c0000000-0000-0000-0000-000000000005', 'PENDING', 'Order placed successfully', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
('e0000000-0000-0000-0000-000000000014', 'c0000000-0000-0000-0000-000000000005', 'PROCESSING', 'Payment confirmed, order being processed', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
('e0000000-0000-0000-0000-000000000015', 'c0000000-0000-0000-0000-000000000005', 'CANCELLED', 'Order cancelled by customer', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days');
