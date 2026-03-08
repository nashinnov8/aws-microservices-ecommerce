package com.ecommerce.productservice.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for events published by product-service to the "product-events" topic.
 * Consumed by inventory-service to auto-create/update/delete inventory records.
 *
 * @param eventId     Unique event identifier (for idempotency/deduplication)
 * @param eventType   Event type: VARIANT_CREATED, VARIANT_UPDATED, VARIANT_DELETED, PRODUCT_DELETED
 * @param productId   Product UUID
 * @param variantId   Variant UUID
 * @param sku         Primary business identifier (variant SKU)
 * @param productName Product name (for denormalization in inventory)
 * @param variantName Variant display name like "Red / XL" (for denormalization)
 * @param price       Current final price (for reference)
 * @param timestamp   When the event was created
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
    // ==================== EVENT TYPE CONSTANTS ====================

    public static final String VARIANT_CREATED = "VARIANT_CREATED";
    public static final String VARIANT_UPDATED = "VARIANT_UPDATED";
    public static final String VARIANT_DELETED = "VARIANT_DELETED";
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";

    // ==================== FACTORY METHODS ====================

    /**
     * Create a VARIANT_CREATED event.
     * Published when a new product variant is created.
     */
    public static ProductEvent variantCreated(UUID productId, UUID variantId,
                                               String sku, String productName,
                                               String variantName, BigDecimal price) {
        return new ProductEvent(
                UUID.randomUUID().toString(),
                VARIANT_CREATED,
                productId,
                variantId,
                sku,
                productName,
                variantName,
                price,
                Instant.now()
        );
    }

    /**
     * Create a VARIANT_UPDATED event.
     * Published when a product variant's metadata changes.
     */
    public static ProductEvent variantUpdated(UUID productId, UUID variantId,
                                               String sku, String productName,
                                               String variantName, BigDecimal price) {
        return new ProductEvent(
                UUID.randomUUID().toString(),
                VARIANT_UPDATED,
                productId,
                variantId,
                sku,
                productName,
                variantName,
                price,
                Instant.now()
        );
    }

    /**
     * Create a VARIANT_DELETED event.
     * Published when a single variant is deleted.
     */
    public static ProductEvent variantDeleted(UUID productId, UUID variantId, String sku) {
        return new ProductEvent(
                UUID.randomUUID().toString(),
                VARIANT_DELETED,
                productId,
                variantId,
                sku,
                null,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Create a PRODUCT_DELETED event.
     * Published once per variant when an entire product is deleted.
     */
    public static ProductEvent productDeleted(UUID productId, UUID variantId, String sku) {
        return new ProductEvent(
                UUID.randomUUID().toString(),
                PRODUCT_DELETED,
                productId,
                variantId,
                sku,
                null,
                null,
                null,
                Instant.now()
        );
    }

    // ==================== CONVENIENCE CHECKS ====================

    public boolean isCreateEvent() {
        return VARIANT_CREATED.equals(eventType);
    }

    public boolean isUpdateEvent() {
        return VARIANT_UPDATED.equals(eventType);
    }

    public boolean isDeleteEvent() {
        return VARIANT_DELETED.equals(eventType) || PRODUCT_DELETED.equals(eventType);
    }
}

