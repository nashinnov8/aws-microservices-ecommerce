package com.ecommerce.inventoryservice.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for order events consumed from order-service.
 * Used for automatic reservation fulfillment/release.
 *
 * @param eventId Unique event identifier
 * @param eventType Event type: ORDER_COMPLETED, ORDER_CANCELLED, ORDER_PAYMENT_FAILED
 * @param orderId Order identifier
 * @param items Order line items with SKU and quantity
 * @param timestamp When the event occurred
 */
public record OrderEvent(
        String eventId,
        String eventType,
        String orderId,
        List<OrderItem> items,
        Instant timestamp
) {
    /**
     * Event type constants.
     */
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_PAYMENT_FAILED = "ORDER_PAYMENT_FAILED";

    /**
     * Order line item.
     */
    public record OrderItem(
            String sku,
            UUID variantId,
            int quantity
    ) {}
}
