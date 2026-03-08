package com.ecommerce.productservice.kafka;

import com.ecommerce.productservice.domain.entity.ProductVariant;
import com.ecommerce.productservice.domain.enums.ProductStatus;
import com.ecommerce.productservice.domain.repository.ProductVariantRepository;
import com.ecommerce.productservice.dto.event.InventoryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Consumes inventory events from inventory-service.
 * Tracks stock availability and updates product variant status accordingly.
 *
 * Events handled:
 * - OUT_OF_STOCK  → Mark variant as INACTIVE (no stock available)
 * - BACK_IN_STOCK → Mark variant as ACTIVE (stock recovered)
 * - LOW_STOCK_ALERT → Log warning (could trigger notifications)
 * - STOCK_UPDATED, STOCK_RESERVED, STOCK_RELEASED → Log for observability
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final ProductVariantRepository productVariantRepository;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(
            topics = "inventory-events",
            groupId = "product-service",
            containerFactory = "inventoryEventListenerContainerFactory"
    )
    @Transactional
    public void handleInventoryEvent(String message) {
        try {
            InventoryEvent event = kafkaObjectMapper.readValue(message, InventoryEvent.class);
            log.info("Received inventory event: {} for SKU: {}", event.eventType(), event.sku());

            switch (event.eventType()) {
                case InventoryEvent.OUT_OF_STOCK -> handleOutOfStock(event);
                case InventoryEvent.BACK_IN_STOCK -> handleBackInStock(event);
                case InventoryEvent.LOW_STOCK_ALERT -> handleLowStockAlert(event);
                case InventoryEvent.STOCK_UPDATED -> log.debug("Stock updated for SKU: {}, new qty: {}",
                        event.sku(), event.newQuantity());
                case InventoryEvent.STOCK_RESERVED -> log.debug("Stock reserved for SKU: {}, reserved: {}",
                        event.sku(), event.reservedQuantity());
                case InventoryEvent.STOCK_RELEASED -> log.debug("Stock released for SKU: {}, qty: {}",
                        event.sku(), event.newQuantity());
                default -> log.warn("Unknown inventory event type: {}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Error processing inventory event message: {}", message, e);
            throw new RuntimeException("Failed to process inventory event", e);
        }
    }

    /**
     * Handle OUT_OF_STOCK — mark the variant as INACTIVE.
     * This makes it invisible or shows "out of stock" on the storefront.
     */
    private void handleOutOfStock(InventoryEvent event) {
        log.warn("OUT OF STOCK: SKU {} is out of stock", event.sku());

        findVariantBySku(event.sku()).ifPresentOrElse(
                variant -> {
                    if (variant.getStatus() != ProductStatus.INACTIVE) {
                        variant.setStatus(ProductStatus.INACTIVE);
                        productVariantRepository.save(variant);
                        log.info("Variant {} (SKU: {}) marked as INACTIVE due to out-of-stock",
                                variant.getId(), event.sku());
                    }
                },
                () -> log.warn("No variant found for SKU: {} — cannot mark as out-of-stock", event.sku())
        );
    }

    /**
     * Handle BACK_IN_STOCK — mark the variant as ACTIVE again.
     */
    private void handleBackInStock(InventoryEvent event) {
        log.info("BACK IN STOCK: SKU {} now has {} units", event.sku(), event.newQuantity());

        findVariantBySku(event.sku()).ifPresentOrElse(
                variant -> {
                    if (variant.getStatus() == ProductStatus.INACTIVE) {
                        variant.setStatus(ProductStatus.ACTIVE);
                        productVariantRepository.save(variant);
                        log.info("Variant {} (SKU: {}) marked as ACTIVE — back in stock",
                                variant.getId(), event.sku());
                    }
                },
                () -> log.warn("No variant found for SKU: {} — cannot mark as back-in-stock", event.sku())
        );
    }

    /**
     * Handle LOW_STOCK_ALERT — log the warning.
     * In production, this could trigger email notifications or dashboard alerts.
     */
    private void handleLowStockAlert(InventoryEvent event) {
        log.warn("LOW STOCK ALERT: SKU {} has {} units. Reason: {}",
                event.sku(), event.newQuantity(), event.reason());
        // Future: publish notification event, send email, update dashboard, etc.
    }

    /**
     * Find a variant by SKU. Centralised lookup for reuse.
     */
    private Optional<ProductVariant> findVariantBySku(String sku) {
        return productVariantRepository.findByVariantSku(sku);
    }
}




