package com.ecommerce.productservice.kafka;

import com.ecommerce.productservice.domain.entity.Product;
import com.ecommerce.productservice.domain.entity.ProductVariant;
import com.ecommerce.productservice.dto.event.ProductEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for publishing product events.
 *
 * Events published to "product-events" topic:
 * - VARIANT_CREATED  → when a new product variant is created
 * - VARIANT_UPDATED  → when a product variant is updated
 * - VARIANT_DELETED  → when a single variant is deleted
 * - PRODUCT_DELETED  → when an entire product is deleted (one event per variant)
 *
 * Uses variant SKU as the message key for partition consistency,
 * ensuring all events for the same SKU go to the same partition.
 *
 * These events are consumed by inventory-service to automatically
 * create/update/deactivate inventory records.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventProducer {

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;

    @Value("${kafka.topics.product-events:product-events}")
    private String productEventsTopic;

    /**
     * Publish a VARIANT_CREATED event after a new variant is persisted.
     *
     * @param variant The newly created variant (already saved)
     */
    public void publishVariantCreated(ProductVariant variant) {
        Product product = variant.getProduct();
        String variantName = buildVariantName(variant);

        ProductEvent event = ProductEvent.variantCreated(
                product.getId(),
                variant.getId(),
                variant.getVariantSku(),
                product.getName(),
                variantName,
                variant.getFinalPrice()
        );

        sendEvent(variant.getVariantSku(), event);
    }

    /**
     * Publish a VARIANT_UPDATED event after a variant is modified.
     *
     * @param variant The updated variant (already saved)
     */
    public void publishVariantUpdated(ProductVariant variant) {
        Product product = variant.getProduct();
        String variantName = buildVariantName(variant);

        ProductEvent event = ProductEvent.variantUpdated(
                product.getId(),
                variant.getId(),
                variant.getVariantSku(),
                product.getName(),
                variantName,
                variant.getFinalPrice()
        );

        sendEvent(variant.getVariantSku(), event);
    }

    /**
     * Publish a VARIANT_DELETED event when a single variant is removed.
     *
     * @param variant The variant being deleted (before deletion)
     */
    public void publishVariantDeleted(ProductVariant variant) {
        ProductEvent event = ProductEvent.variantDeleted(
                variant.getProduct().getId(),
                variant.getId(),
                variant.getVariantSku()
        );

        sendEvent(variant.getVariantSku(), event);
    }

    /**
     * Publish a PRODUCT_DELETED event for each variant of the deleted product.
     * Called once per variant when an entire product is removed.
     *
     * @param product The product being deleted (before deletion, with variants loaded)
     */
    public void publishProductDeleted(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            log.info("Product {} has no variants, no PRODUCT_DELETED events to publish", product.getId());
            return;
        }

        for (ProductVariant variant : product.getVariants()) {
            ProductEvent event = ProductEvent.productDeleted(
                    product.getId(),
                    variant.getId(),
                    variant.getVariantSku()
            );
            sendEvent(variant.getVariantSku(), event);
        }
    }

    /**
     * Publish VARIANT_UPDATED events for all variants of a product.
     * Called when product-level fields (name, price) change, affecting all variants.
     *
     * @param product The updated product (with variants loaded)
     */
    public void publishProductUpdated(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            log.info("Product {} has no variants, no VARIANT_UPDATED events to publish", product.getId());
            return;
        }

        for (ProductVariant variant : product.getVariants()) {
            publishVariantUpdated(variant);
        }
    }

    // ==================== INTERNAL ====================

    /**
     * Build a human-readable variant name from size/color/material.
     * Example: "Red / XL" or "Leather / Black / M"
     */
    private String buildVariantName(ProductVariant variant) {
        StringBuilder sb = new StringBuilder();
        if (variant.getColor() != null && !variant.getColor().isBlank()) {
            sb.append(variant.getColor());
        }
        if (variant.getSize() != null && !variant.getSize().isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(variant.getSize());
        }
        if (variant.getMaterial() != null && !variant.getMaterial().isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(variant.getMaterial());
        }
        return sb.isEmpty() ? variant.getVariantSku() : sb.toString();
    }

    /**
     * Send an event to Kafka.
     * Uses SKU as the message key to ensure ordering per SKU.
     *
     * @param sku   Message key (ensures same partition for same SKU)
     * @param event The product event to send
     */
    private void sendEvent(String sku, ProductEvent event) {
        log.debug("Publishing {} event for SKU: {}, eventId: {}",
                event.eventType(), sku, event.eventId());

        CompletableFuture<SendResult<String, ProductEvent>> future =
                kafkaTemplate.send(productEventsTopic, sku, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published {} event for SKU: {} to partition {} with offset {}",
                        event.eventType(),
                        sku,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish {} event for SKU: {}: {}",
                        event.eventType(), sku, ex.getMessage(), ex);
                // In production: retry with backoff, store for later, or send to DLT
            }
        });
    }
}

