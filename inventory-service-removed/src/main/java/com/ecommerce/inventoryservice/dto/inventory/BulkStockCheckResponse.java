package com.ecommerce.inventoryservice.dto.inventory;


import java.util.List;

/**
 * Response DTO for bulk stock check.
 *
 * @param allAvailable True if all requested quantities are available
 * @param items Individual check results for each SKU
 */
public record BulkStockCheckResponse(
        boolean allAvailable,
        List<StockCheckResult> items
) {
    /**
     * Result for individual item in bulk check.
     *
     * @param sku The SKU that was checked
     * @param requestedQuantity Quantity that was requested
     * @param availableQuantity Actual available quantity
     * @param isAvailable True if requestedQuantity <= availableQuantity
     * @param shortfall How much more stock is needed (0 if available)
     */
    public record StockCheckResult(
            String sku,
            int requestedQuantity,
            int availableQuantity,
            boolean isAvailable,
            int shortfall
    ) {}
}