package com.ecommerce.productservice.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for events consumed from inventory-service via the "inventory-events" topic.
 * Used to track stock availability status on the product side
 * (e.g., marking variants as OUT_OF_STOCK or BACK_IN_STOCK).
 *
 * @param eventId          Unique event identifier
 * @param eventType        Event type: STOCK_UPDATED, LOW_STOCK_ALERT, STOCK_RESERVED, etc.
 * @param inventoryId      Inventory record UUID
 * @param sku              Item SKU
 * @param variantId        Variant UUID (for cross-referencing)
 * @param productId        Product UUID (for grouping)
 * @param previousQuantity Stock before change
 * @param newQuantity      Stock after change
 * @param reservedQuantity Current reserved stock
 * @param reason           Reason for the change
 * @param referenceId      External reference (e.g., order ID)
 * @param timestamp        When the event occurred
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
    // ==================== EVENT TYPE CONSTANTS ====================

    public static final String STOCK_UPDATED = "STOCK_UPDATED";
    public static final String LOW_STOCK_ALERT = "LOW_STOCK_ALERT";
    public static final String STOCK_RESERVED = "STOCK_RESERVED";
    public static final String STOCK_RELEASED = "STOCK_RELEASED";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String BACK_IN_STOCK = "BACK_IN_STOCK";

    /**
     * Check if available stock (newQuantity - reservedQuantity) is zero.
     */
    public boolean isOutOfStock() {
        return (newQuantity - reservedQuantity) <= 0;
    }
}

