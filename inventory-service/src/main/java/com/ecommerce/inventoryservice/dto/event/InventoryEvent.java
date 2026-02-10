package com.ecommerce.inventoryservice.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for events published by inventory-service.
 *
 * @param eventId Unique event identifier
 * @param eventType Event type: STOCK_UPDATED, LOW_STOCK_ALERT, STOCK_RESERVED, STOCK_RELEASED
 * @param inventoryId Inventory record UUID
 * @param sku Item SKU
 * @param variantId Variant UUID (for cross-referencing)
 * @param productId Product UUID (for grouping)
 * @param previousQuantity Stock before change
 * @param newQuantity Stock after change
 * @param reservedQuantity Current reserved stock
 * @param reason Reason for the change
 * @param referenceId External reference (e.g., order ID)
 * @param timestamp When the event occurred
 */
public record InventoryEvent(
        String eventId,
        String eventType,
        UUID inventoryId,
        String sku,
        UUID variantId,
        UUID productId,
        int previousQuantity,
        int newQuantity,
        int reservedQuantity,
        String reason,
        String referenceId,
        Instant timestamp
) {
    /**
     * Event type constants.
     */
    public static final String STOCK_UPDATED = "STOCK_UPDATED";
    public static final String LOW_STOCK_ALERT = "LOW_STOCK_ALERT";
    public static final String STOCK_RESERVED = "STOCK_RESERVED";
    public static final String STOCK_RELEASED = "STOCK_RELEASED";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String BACK_IN_STOCK = "BACK_IN_STOCK";

    /**
     * Factory method for stock update event.
     */
    public static InventoryEvent stockUpdated(UUID inventoryId, String sku, UUID variantId,
                                               UUID productId, int previousQty, int newQty,
                                               int reservedQty, String reason) {
        return new InventoryEvent(
                UUID.randomUUID().toString(),
                STOCK_UPDATED,
                inventoryId,
                sku,
                variantId,
                productId,
                previousQty,
                newQty,
                reservedQty,
                reason,
                null,
                Instant.now()
        );
    }

    /**
     * Factory method for low stock alert event.
     */
    public static InventoryEvent lowStockAlert(UUID inventoryId, String sku, UUID variantId,
                                                UUID productId, int currentStock, int reorderPoint) {
        return new InventoryEvent(
                UUID.randomUUID().toString(),
                LOW_STOCK_ALERT,
                inventoryId,
                sku,
                variantId,
                productId,
                currentStock,
                currentStock,
                0,
                "Stock fell to " + currentStock + " (reorder point: " + reorderPoint + ")",
                null,
                Instant.now()
        );
    }

    /**
     * Factory method for stock reserved event.
     */
    public static InventoryEvent stockReserved(UUID inventoryId, String sku, UUID variantId,
                                                UUID productId, int quantity, String orderId) {
        return new InventoryEvent(
                UUID.randomUUID().toString(),
                STOCK_RESERVED,
                inventoryId,
                sku,
                variantId,
                productId,
                0,
                quantity,
                quantity,
                "Stock reserved for order",
                orderId,
                Instant.now()
        );
    }

    /**
     * Factory method for stock released event.
     */
    public static InventoryEvent stockReleased(UUID inventoryId, String sku, UUID variantId,
                                                UUID productId, int quantity, String orderId,
                                                String reason) {
        return new InventoryEvent(
                UUID.randomUUID().toString(),
                STOCK_RELEASED,
                inventoryId,
                sku,
                variantId,
                productId,
                0,
                quantity,
                0,
                reason,
                orderId,
                Instant.now()
        );
    }
}
