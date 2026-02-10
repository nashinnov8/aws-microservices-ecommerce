package com.ecommerce.inventoryservice.dto.inventory;


import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO containing full stock information for an inventory item.
 *
 * @param id Inventory record UUID
 * @param sku Primary business identifier
 * @param variantId Reference to product-service variant
 * @param productId Reference to product-service product
 * @param productName Denormalized product name
 * @param variantName Denormalized variant name
 * @param availableStock Stock available for sale
 * @param reservedStock Stock reserved for pending orders
 * @param totalStock availableStock + reservedStock
 * @param lowStock True if availableStock <= reorderPoint
 * @param reorderPoint Threshold for low stock alerts
 * @param minStockLevel Minimum desired stock level
 * @param maxStockLevel Maximum warehouse capacity for this item
 * @param isActive Whether this inventory is active (false = archived)
 * @param createdAt When the inventory record was created
 * @param updatedAt When the inventory was last updated
 */
public record StockInfoResponse(
        UUID id,
        String sku,
        UUID variantId,
        UUID productId,
        String productName,
        String variantName,
        int availableStock,
        int reservedStock,
        int totalStock,
        boolean lowStock,
        int reorderPoint,
        int minStockLevel,
        int maxStockLevel,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Factory method for simplified response (commonly used fields only).
     */
    public static StockInfoResponse simple(UUID id, String sku, int availableStock,
                                           int reservedStock, boolean lowStock) {
        return new StockInfoResponse(
                id, sku, null, null, null, null,
                availableStock, reservedStock, availableStock + reservedStock,
                lowStock, 0, 0, 0, true, null, null
        );
    }
}