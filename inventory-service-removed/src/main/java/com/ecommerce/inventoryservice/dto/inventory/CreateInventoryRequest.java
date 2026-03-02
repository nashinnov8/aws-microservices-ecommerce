package com.ecommerce.inventoryservice.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new inventory item (ADMIN operation).
 * Normally inventory is created automatically via Kafka event from product-service.
 *
 * @param sku Primary business identifier (must match ProductVariant.variantSku)
 * @param variantId UUID of the variant in product-service
 * @param productId UUID of the product in product-service
 * @param productName Denormalized product name for display
 * @param variantName Denormalized variant name (e.g., "Red / XL")
 * @param initialStock Starting stock quantity (default 0)
 * @param minStockLevel Minimum stock threshold for alerts
 * @param maxStockLevel Maximum stock capacity
 * @param reorderPoint Level at which to trigger low stock alert
 */
public record CreateInventoryRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Variant ID is required")
        UUID variantId,

        @NotNull(message = "Product ID is required")
        UUID productId,

        String productName,
        String variantName,

        @Min(value = 0, message = "Initial stock cannot be negative")
        int initialStock,

        @Min(value = 0, message = "Max stock level cannot be negative")
        Integer maxStockLevel,

        @Min(value = 0, message = "Max stock level cannot be negative")
        Integer minStockLevel,

        @Min(value = 0, message = "Reorder point cannot be negative")
        Integer reorderPoint
) {
    public CreateInventoryRequest {
        if (minStockLevel == null) minStockLevel = 10;
        if (maxStockLevel == null) maxStockLevel = 1000;
        if (reorderPoint == null) reorderPoint = 20;
    }
}
