-- =====================================================
-- V1: Initial schema for order-service
-- =====================================================

-- Orders table: Core order tracking
CREATE TABLE IF NOT EXISTS orders (
                                      id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    order_number VARCHAR(50) NOT NULL COMMENT 'Human-readable order number (e.g., ORD-20260302-XXXX)',
    user_id VARCHAR(100) NOT NULL COMMENT 'User ID from auth-service',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'Order status',
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT 'Total order amount',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' COMMENT 'Currency code',
    shipping_address_line1 VARCHAR(255) COMMENT 'Shipping address line 1',
    shipping_address_line2 VARCHAR(255) COMMENT 'Shipping address line 2',
    shipping_city VARCHAR(100) COMMENT 'Shipping city',
    shipping_state VARCHAR(100) COMMENT 'Shipping state/province',
    shipping_zip_code VARCHAR(20) COMMENT 'Shipping zip/postal code',
    shipping_country VARCHAR(100) COMMENT 'Shipping country',
    notes TEXT COMMENT 'Order notes from customer',
    cancelled_reason VARCHAR(500) COMMENT 'Reason for cancellation',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Unique constraints
    UNIQUE KEY uk_order_number (order_number),

    -- Indexes for query performance
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_status (status),
    KEY idx_orders_created_at (created_at),
    KEY idx_orders_user_status (user_id, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Order items table: Line items for each order
CREATE TABLE IF NOT EXISTS order_items (
                                           id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    order_id BINARY(16) NOT NULL COMMENT 'Reference to orders table',
    sku VARCHAR(100) NOT NULL COMMENT 'SKU from inventory-service',
    product_id BINARY(16) NOT NULL COMMENT 'Product UUID from product-service',
    variant_id BINARY(16) NOT NULL COMMENT 'Variant UUID from product-service',
    product_name VARCHAR(255) NOT NULL COMMENT 'Denormalized product name at time of order',
    variant_name VARCHAR(255) COMMENT 'Denormalized variant name (e.g., Red / XL)',
    quantity INT NOT NULL COMMENT 'Quantity ordered',
    unit_price DECIMAL(12, 2) NOT NULL COMMENT 'Price per unit at time of order',
    subtotal DECIMAL(12, 2) NOT NULL COMMENT 'quantity * unit_price',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Foreign keys
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,

    -- Indexes
    KEY idx_oi_order_id (order_id),
    KEY idx_oi_sku (sku),
    KEY idx_oi_product_id (product_id),

    -- Check constraints
    CONSTRAINT chk_oi_quantity CHECK (quantity > 0),
    CONSTRAINT chk_oi_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_oi_subtotal CHECK (subtotal >= 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;