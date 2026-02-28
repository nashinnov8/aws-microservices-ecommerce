package com.ecommerce.inventoryservice.dto.inventory;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request DTO for checking stock of multiple SKUs at once.
 * Used by order-service to validate cart before checkout.
 *
 * @param items List of SKU + quantity pairs to check
 */
public record BulkStockCheckRequest(
        @NotEmpty(message = "Items list cannot be empty")
        List<StockCheckItem> items
) {
    /**
     * Individual item in bulk stock check.
     *
     * @param sku SKU to check
     * @param requestedQuantity Quantity needed
     */
    public record StockCheckItem(
            String sku,
            int requestedQuantity
    ) {}
}