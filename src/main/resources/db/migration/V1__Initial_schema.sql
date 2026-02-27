-- Polaroid Glossy Database Migration V1
-- Initial schema for Polaroid Glossy Backend

-- =============================================
-- USERS TABLE
-- =============================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    phone VARCHAR(50),
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    affiliate_code VARCHAR(50) UNIQUE,
    referred_by UUID REFERENCES users(id),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Single Column Indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_affiliate_code ON users(affiliate_code);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_is_active ON users(is_active);
CREATE INDEX idx_users_referred_by ON users(referred_by);
CREATE INDEX idx_users_created_at ON users(created_at);

-- Composite Indexes
CREATE INDEX idx_users_role_is_active ON users(role, is_active);

-- =============================================
-- ORDERS TABLE
-- =============================================
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    affiliate_id UUID REFERENCES users(id) ON DELETE SET NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(50),
    customer_state VARCHAR(10) DEFAULT 'W',
    
    status VARCHAR(20) DEFAULT 'PENDING',
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    payment_method VARCHAR(20),
    toyyibpay_ref VARCHAR(100),
    
    subtotal DECIMAL(10,2) NOT NULL,
    shipping DECIMAL(10,2) DEFAULT 0,
    total DECIMAL(10,2) NOT NULL,
    
    paid_at TIMESTAMPTZ,
    tracking_number VARCHAR(100),
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    notes TEXT,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Single Column Indexes
CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_affiliate_id ON orders(affiliate_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_customer_email ON orders(customer_email);
CREATE INDEX idx_orders_tracking_number ON orders(tracking_number);
CREATE INDEX idx_orders_toyyibpay_ref ON orders(toyyibpay_ref);

-- Composite Indexes (for common query patterns)
CREATE INDEX idx_orders_status_payment ON orders(status, payment_status);
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_orders_created_status ON orders(created_at DESC, status);
CREATE INDEX idx_orders_payment_paid_at ON orders(payment_status, paid_at);

-- =============================================
-- ORDER ITEMS TABLE
-- =============================================
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    size_id VARCHAR(10) NOT NULL,
    size_name VARCHAR(20) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    images JSONB NOT NULL DEFAULT '[]',
    custom_texts JSONB DEFAULT '[]',
    s3_keys JSONB DEFAULT '[]',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Single Column Indexes
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_size_id ON order_items(size_id);

-- Composite Indexes
CREATE INDEX idx_order_items_order_size ON order_items(order_id, size_id);

-- =============================================
-- ORDER STATUS HISTORY TABLE
-- =============================================
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Single Column Indexes
CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);
CREATE INDEX idx_order_status_history_status ON order_status_history(status);
CREATE INDEX idx_order_status_history_created_at ON order_status_history(created_at DESC);

-- =============================================
-- PRINT SIZES TABLE (Reference Data)
-- =============================================
CREATE TABLE print_sizes (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    width DECIMAL(5,2) NOT NULL,
    height DECIMAL(5,2) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Single Column Indexes
CREATE INDEX idx_print_sizes_is_active ON print_sizes(is_active);
CREATE INDEX idx_print_sizes_price ON print_sizes(price);

-- Default print sizes
INSERT INTO print_sizes (id, name, display_name, width, height, price, description) VALUES
('2R', '2R', '2R (2.5 x 3.5 inches)', 2.5, 3.5, 0.50, 'Wallet size - Perfect for keepsakes'),
('3R', '3R', '3R (3.5 x 5 inches)', 3.5, 5.0, 0.75, 'Standard photo size - Great for albums'),
('4R', '4R', '4R (4 x 6 inches)', 4.0, 6.0, 1.00, 'Most popular - Classic polaroid style'),
('A4', 'A4', 'A4 (8.3 x 11.7 inches)', 8.3, 11.7, 3.50, 'Poster size - Perfect for displays');

-- =============================================
-- ANALYTICS VIEWS (Materialized for Performance)
-- =============================================

-- Materialized view for daily sales summary
CREATE MATERIALIZED VIEW mv_daily_sales AS
SELECT 
    DATE(created_at) as sale_date,
    COUNT(*) as order_count,
    SUM(total) as total_revenue,
    SUM(CASE WHEN payment_status = 'PAID' THEN total ELSE 0 END) as paid_revenue
FROM orders
GROUP BY DATE(created_at);

CREATE INDEX mv_daily_sales_date ON mv_daily_sales(sale_date DESC);

-- Materialized view for orders by status
CREATE MATERIALIZED VIEW mv_orders_by_status AS
SELECT 
    status,
    payment_status,
    customer_state,
    COUNT(*) as order_count,
    SUM(total) as total_amount
FROM orders
GROUP BY status, payment_status, customer_state;

-- Materialized view for top selling sizes
CREATE MATERIALIZED VIEW mv_top_sizes AS
SELECT 
    oi.size_id,
    oi.size_name,
    SUM(oi.quantity) as total_prints,
    COUNT(DISTINCT o.order_id) as order_count
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.payment_status = 'PAID'
GROUP BY oi.size_id, oi.size_name
ORDER BY total_prints DESC;

CREATE INDEX mv_top_sizes_total ON mv_top_sizes(total_prints DESC);
