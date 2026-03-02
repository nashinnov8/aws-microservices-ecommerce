-- =====================================================
-- V1: Initial schema for inventory-service
-- =====================================================

-- Inventory table: Core SKU-level stock tracking
CREATE TABLE IF NOT EXISTS inventory (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    sku VARCHAR(100) NOT NULL COMMENT 'SKU - primary business key',
    variant_id BINARY(16) NOT NULL COMMENT 'Product variant UUID from product-service',
    product_id BINARY(16) NOT NULL COMMENT 'Product UUID from product-service',
    product_name VARCHAR(255) COMMENT 'Denormalized product name',
    variant_name VARCHAR(255) COMMENT 'Denormalized variant name',
    available_stock INT NOT NULL DEFAULT 0,
    reserved_stock INT NOT NULL DEFAULT 0,
    min_stock_level INT NOT NULL DEFAULT 10,
    max_stock_level INT NOT NULL DEFAULT 1000,
    reorder_point INT NOT NULL DEFAULT 20,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Unique constraints
    UNIQUE KEY uk_inventory_sku (sku),
    UNIQUE KEY uk_inventory_variant (product_id, variant_id),

    -- Indexes for query performance
    KEY idx_inventory_sku (sku),
    KEY idx_inventory_variant_id (variant_id),
    KEY idx_inventory_product_id (product_id),
    KEY idx_inventory_is_active (is_active),

    -- Check constraints for data integrity
    CONSTRAINT chk_available_stock CHECK (available_stock >= 0),
    CONSTRAINT chk_reserved_stock CHECK (reserved_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Stock reservations table: Order-level stock reservations
CREATE TABLE IF NOT EXISTS stock_reservations (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    inventory_id BINARY(16) NOT NULL COMMENT 'Reference to inventory',
    order_id VARCHAR(100) NOT NULL COMMENT 'Order UUID or ID',
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, RESERVED, FULFILLED, RELEASED, EXPIRED, CANCELLED',
    expires_at TIMESTAMP(6) NOT NULL COMMENT 'Reservation expiry time',
    fulfilled_at TIMESTAMP(6) COMMENT 'When reservation was fulfilled',
    cancelled_at TIMESTAMP(6) COMMENT 'When reservation was cancelled/released',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Indexes
    KEY idx_sr_inventory_id (inventory_id),
    KEY idx_sr_order_id (order_id),
    KEY idx_sr_status (status),
    KEY idx_sr_expires_at (expires_at),

    -- Check constraints
    CONSTRAINT chk_sr_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Stock movements table: Audit trail for all inventory changes
CREATE TABLE IF NOT EXISTS stock_movements (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    inventory_id BINARY(16) NOT NULL COMMENT 'Reference to inventory',
    warehouse_id BINARY(16) COMMENT 'Reference to warehouse (optional)',
    movement_type VARCHAR(20) NOT NULL COMMENT 'RECEIVE, SHIP, ADJUST, RESERVE, RELEASE, TRANSFER_IN, TRANSFER_OUT',
    quantity INT NOT NULL,
    previous_quantity INT NOT NULL,
    new_quantity INT NOT NULL,
    reason VARCHAR(500) COMMENT 'Movement reason',
    reference_id VARCHAR(100) COMMENT 'External reference (order ID, PO number, etc.)',
    performed_by VARCHAR(100) COMMENT 'User or system that performed movement',
    performed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Indexes
    KEY idx_sm_inventory_id (inventory_id),
    KEY idx_sm_warehouse_id (warehouse_id),
    KEY idx_sm_movement_type (movement_type),
    KEY idx_sm_performed_at (performed_at),
    KEY idx_sm_reference_id (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Warehouses table: Physical warehouse locations
CREATE TABLE IF NOT EXISTS warehouses (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    warehouse_code VARCHAR(50) NOT NULL COMMENT 'e.g., WH-SYD-01',
    name VARCHAR(100) NOT NULL COMMENT 'Warehouse name',
    location VARCHAR(255) COMMENT 'Location description',
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    zip_code VARCHAR(20),
    capacity INT NOT NULL DEFAULT 10000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Unique constraints
    UNIQUE KEY uk_warehouse_code (warehouse_code),

    -- Indexes
    KEY idx_warehouse_is_active (is_active),

    -- Check constraints
    CONSTRAINT chk_warehouse_capacity CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Warehouse inventory table: Multi-warehouse stock distribution
CREATE TABLE IF NOT EXISTS warehouse_inventory (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    warehouse_id BINARY(16) NOT NULL COMMENT 'Reference to warehouse',
    inventory_id BINARY(16) NOT NULL COMMENT 'Reference to inventory',
    quantity INT NOT NULL DEFAULT 0,
    aisle VARCHAR(20) COMMENT 'Aisle location',
    rack VARCHAR(20) COMMENT 'Rack location',
    shelf VARCHAR(20) COMMENT 'Shelf location',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Unique constraints
    UNIQUE KEY uk_warehouse_inventory (warehouse_id, inventory_id),

    -- Indexes
    KEY idx_wi_warehouse_id (warehouse_id),
    KEY idx_wi_inventory_id (inventory_id),

    -- Check constraints
    CONSTRAINT chk_wi_quantity CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

