package com.ecommerce.inventoryservice.dto.warehouse;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for stock transfer operations.
 *
 * @param transferId Unique ID for this transfer
 * @param sku SKU that was transferred
 * @param quantity Quantity transferred
 * @param fromWarehouse Source warehouse info
 * @param toWarehouse Destination warehouse info
 * @param performedBy Who performed the transfer
 * @param performedAt When the transfer occurred
 */
public record TransferStockResponse(
        UUID transferId,
        String sku,
        int quantity,
        WarehouseSnapshot fromWarehouse,
        WarehouseSnapshot toWarehouse,
        String performedBy,
        Instant performedAt
) {
    /**
     * Snapshot of warehouse state after transfer.
     */
    public record WarehouseSnapshot(
            UUID id,
            String warehouseCode,
            String name,
            int quantityBefore,
            int quantityAfter
    ) {}
}
