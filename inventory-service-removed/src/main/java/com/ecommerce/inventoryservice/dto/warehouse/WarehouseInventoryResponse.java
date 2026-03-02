package com.ecommerce.inventoryservice.dto.warehouse;

import java.util.UUID;

/**
 * Response DTO for warehouse-inventory mapping.
 * Shows stock of a specific item in a specific warehouse.
 *
 * @param warehouseId Warehouse UUID
 * @param warehouseCode Warehouse code
 * @param warehouseName Warehouse name
 * @param inventoryId Inventory UUID
 * @param sku Item SKU
 * @param quantity Stock in this warehouse
 * @param location Physical location within warehouse
 */
public record WarehouseInventoryResponse(
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        UUID inventoryId,
        String sku,
        int quantity,
        WarehouseLocation location
) {
    /**
     * Physical location within warehouse.
     */
    public record WarehouseLocation(
            String aisle,
            String rack,
            String shelf,
            String fullLocation
    ) {}
}
