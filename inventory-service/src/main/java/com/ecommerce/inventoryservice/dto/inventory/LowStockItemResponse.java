package com.ecommerce.inventoryservice.dto.inventory;

import java.util.UUID;

/**
 * Response DTO for low stock items report.
 * Simplified version of StockInfoResponse for alert lists.
 *
 * @param id Inventory record UUID
 * @param sku Primary business identifier
 * @param productName Product name for display
 * @param variantName Variant name for display
 * @param availableStock Current available stock
 * @param reorderPoint The threshold that was breached
 */
public record LowStockItemResponse(
        UUID id,
        String sku,
        String productName,
        String variantName,
        int availableStock,
        int reorderPoint
) {}