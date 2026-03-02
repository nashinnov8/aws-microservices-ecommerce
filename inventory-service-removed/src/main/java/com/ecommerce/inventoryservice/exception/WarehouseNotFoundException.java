package com.ecommerce.inventoryservice.exception;

import java.util.UUID;

/**
 * Exception thrown when a warehouse cannot be found.
 * This can occur when looking up by:
 * - Warehouse code (primary business key)
 * - Warehouse UUID
 */
public class WarehouseNotFoundException extends RuntimeException {

    private final String warehouseCode;
    private final UUID warehouseId;

    public WarehouseNotFoundException(String message) {
        super(message);
        this.warehouseCode = null;
        this.warehouseId = null;
    }

    public static WarehouseNotFoundException byCode(String warehouseCode) {
        return new WarehouseNotFoundException(
                "Warehouse not found with code: " + warehouseCode);
    }

    public static WarehouseNotFoundException byId(UUID warehouseId) {
        return new WarehouseNotFoundException(
                "Warehouse not found with ID: " + warehouseId);
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }
}
