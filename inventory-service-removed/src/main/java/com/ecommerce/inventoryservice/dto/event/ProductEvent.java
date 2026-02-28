package com.ecommerce.inventoryservice.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for events consumed from product-service.
 *
 * @param eventId Unique event identifier (for idempotency/deduplication)
 * @param eventType Event type: VARIANT_CREATED, VARIANT_UPDATED, VARIANT_DELETED, PRODUCT_DELETED
 * @param productId Product UUID in product-service
 * @param variantId Variant UUID in product-service
 * @param sku Primary business identifier
 * @param productName Product name (for denormalization)
 * @param variantName Variant name like "Red / XL" (for denormalization)
 * @param price Current price (for reference, not used for stock)
 * @param timestamp When the event was created
 */
public record ProductEvent(
        String eventId,
        String eventType,
        UUID productId,
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        BigDecimal price,
        Instant timestamp
) {
    /**
     * Event type constants for switch statements.
     */
    public static final String VARIANT_CREATED = "VARIANT_CREATED";
    public static final String VARIANT_UPDATED = "VARIANT_UPDATED";
    public static final String VARIANT_DELETED = "VARIANT_DELETED";
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";

    /**
     * Check if this is a create event.
     */
    public boolean isCreateEvent() {
        return VARIANT_CREATED.equals(eventType);
    }

    /**
     * Check if this is an update event.
     */
    public boolean isUpdateEvent() {
        return VARIANT_UPDATED.equals(eventType);
    }

    /**
     * Check if this is a delete event.
     */
    public boolean isDeleteEvent() {
        return VARIANT_DELETED.equals(eventType) || PRODUCT_DELETED.equals(eventType);
    }
}
