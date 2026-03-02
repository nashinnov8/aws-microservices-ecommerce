package com.ecommerce.inventoryservice.kafka;

import com.ecommerce.inventoryservice.dto.event.ProductEvent;
import com.ecommerce.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "product-events",
            groupId = "inventory-service",
            containerFactory = "productEventListenerContainerFactory"
    )
    public void handleProductEvent(String message) {
        try {
            ProductEvent event = objectMapper.readValue(message, ProductEvent.class);
            log.info("Received product event: {} for variant: {}", event.eventType(), event.variantId());

            switch (event.eventType()) {
                case "VARIANT_CREATED" -> handleVariantCreated(event);
                case "VARIANT_UPDATED" -> handleVariantUpdated(event);
                case "VARIANT_DELETED", "PRODUCT_DELETED" -> handleVariantDeleted(event);
                default -> log.warn("Unknown product event type: {}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Error processing product event message: {}", message, e);
            throw new RuntimeException("Failed to process product event", e);
        }
    }

    private void handleVariantDeleted(ProductEvent event) {
        log.info("Deleting inventory for variant: {}", event.variantId());

        // Soft delete inventory by marking it as inactive
        inventoryService.deactivateInventory(event.sku());
    }

    private void handleVariantUpdated(ProductEvent event) {
        log.info("Updating inventory for variant: {}", event.variantId());

        // Update inventory metadata (e.g., product/variant name) without changing stock levels
        inventoryService.updateInventoryMetadata(
                event.variantId(),
                event.productName(),
                event.variantName()
        );
    }

    private void handleVariantCreated(ProductEvent event) {
        log.info("Creating inventory for variant: {}", event.variantId());

        // Initialize inventory with zero stock for the new variant
        inventoryService.initializeInventory(
                event.sku(),
                event.variantId(),
                event.productId(),
                event.productName(),
                event.variantName(),
                0  // Initial stock level
        );
    }
}
