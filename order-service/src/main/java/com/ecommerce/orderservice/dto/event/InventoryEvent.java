package com.ecommerce.orderservice.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for events consumed from inventory-service.
 * Used by order-service to react to saga responses.
 * <p>
 * Saga-relevant events:
 * - STOCK_RESERVED            → Order transitions PENDING → STOCK_RESERVED
 * - STOCK_RESERVATION_FAILED  → Order transitions PENDING → FAILED
 * - STOCK_RELEASED            → Logged (compensation confirmed)
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
    public static final String STOCK_UPDATED = "STOCK_UPDATED";
    public static final String LOW_STOCK_ALERT = "LOW_STOCK_ALERT";
    public static final String STOCK_RESERVED = "STOCK_RESERVED";
    public static final String STOCK_RELEASED = "STOCK_RELEASED";
    public static final String STOCK_RESERVATION_FAILED = "STOCK_RESERVATION_FAILED";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String BACK_IN_STOCK = "BACK_IN_STOCK";
}