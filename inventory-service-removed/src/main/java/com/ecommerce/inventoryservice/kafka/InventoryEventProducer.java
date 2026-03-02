package com.ecommerce.inventoryservice.kafka;

import com.ecommerce.inventoryservice.dto.event.InventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for publishing inventory events to other services.
 *
 * Events published:
 * - STOCK_UPDATED → when stock level changes
 * - LOW_STOCK_ALERT → when stock falls below reorder point
 * - STOCK_RESERVED → when reservation created
 * - STOCK_RELEASED → when reservation cancelled/expired
 * - OUT_OF_STOCK → when stock reaches zero
 * - BACK_IN_STOCK → when stock recovers from zero
 *
 * Uses SKU as the message key for partition consistency,
 * ensuring all events for the same SKU go to the same partition
 * and are processed in order.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

    private final KafkaTemplate<String, InventoryEvent> kafkaTemplate;

    @Value("${kafka.topics.inventory-events:inventory-events}")
    private String inventoryEventsTopic;

    /**
     * Publish a stock updated event.
     * Called when stock level changes due to any operation.
     *
     * @param inventoryId Inventory record UUID
     * @param sku Item SKU (used as message key)
     * @param variantId Variant UUID
     * @param productId Product UUID
     * @param previousQty Stock before change
     * @param newQty Stock after change
     * @param reservedQty Current reserved stock
     * @param reason Reason for the change
     */
    public void publishStockUpdated(UUID inventoryId, String sku, UUID variantId,
                                     UUID productId, int previousQty, int newQty,
                                     int reservedQty, String reason) {
        InventoryEvent event = InventoryEvent.stockUpdated(
                inventoryId, sku, variantId, productId,
                previousQty, newQty, reservedQty, reason
        );
        sendEvent(sku, event);
    }

    /**
     * Publish a low stock alert event.
     * Called when stock falls to or below the reorder point.
     *
     * @param inventoryId Inventory record UUID
     * @param sku Item SKU (used as message key)
     * @param variantId Variant UUID
     * @param productId Product UUID
     * @param currentStock Current available stock
     * @param reorderPoint The reorder point threshold
     */
    public void publishLowStockAlert(UUID inventoryId, String sku, UUID variantId,
                                      UUID productId, int currentStock, int reorderPoint) {
        InventoryEvent event = InventoryEvent.lowStockAlert(
                inventoryId, sku, variantId, productId,
                currentStock, reorderPoint
        );
        sendEvent(sku, event);
        log.warn("LOW STOCK ALERT: SKU {} has {} units (reorder point: {})",
                sku, currentStock, reorderPoint);
    }

    /**
     * Publish a stock reserved event.
     * Called when stock is reserved for an order.
     *
     * @param inventoryId Inventory record UUID
     * @param sku Item SKU (used as message key)
     * @param variantId Variant UUID
     * @param productId Product UUID
     * @param quantity Quantity reserved
     * @param orderId Order ID for the reservation
     */
    public void publishStockReserved(UUID inventoryId, String sku, UUID variantId,
                                      UUID productId, int quantity, String orderId) {
        InventoryEvent event = InventoryEvent.stockReserved(
                inventoryId, sku, variantId, productId,
                quantity, orderId
        );
        sendEvent(sku, event);
    }

    /**
     * Publish a stock released event.
     * Called when a reservation is cancelled, expired, or fulfilled.
     *
     * @param inventoryId Inventory record UUID
     * @param sku Item SKU (used as message key)
     * @param variantId Variant UUID
     * @param productId Product UUID
     * @param quantity Quantity released
     * @param orderId Order ID for the reservation
     * @param reason Reason for release (cancelled, expired, fulfilled)
     */
    public void publishStockReleased(UUID inventoryId, String sku, UUID variantId,
                                      UUID productId, int quantity, String orderId,
                                      String reason) {
        InventoryEvent event = InventoryEvent.stockReleased(
                inventoryId, sku, variantId, productId,
                quantity, orderId, reason
        );
        sendEvent(sku, event);
    }

    /**
     * Publish an out-of-stock event.
     * Called when available stock reaches zero.
     *
     * @param inventoryId Inventory record UUID
     * @param sku Item SKU (used as message key)
     * @param variantId Variant UUID
     * @param productId Product UUID
     */
    public void publishOutOfStock(UUID inventoryId, String sku, UUID variantId,
                                   UUID productId) {
        InventoryEvent event = new InventoryEvent(
                UUID.randomUUID().toString(),
                InventoryEvent.OUT_OF_STOCK,
                inventoryId,
                sku,
                variantId,
                productId,
                0,
                0,
                0,
                "Item is out of stock",
                null,
                java.time.Instant.now()
        );
        sendEvent(sku, event);
        log.warn("OUT OF STOCK: SKU {} is now out of stock", sku);
    }

    /**
     * Publish a back in stock event.
     * Called when stock recovers from zero.
     *
     * @param inventoryId Inventory record UUID
     * @param sku Item SKU (used as message key)
     * @param variantId Variant UUID
     * @param productId Product UUID
     * @param newStock New available stock quantity
     */
    public void publishBackInStock(UUID inventoryId, String sku, UUID variantId,
                                    UUID productId, int newStock) {
        InventoryEvent event = new InventoryEvent(
                UUID.randomUUID().toString(),
                InventoryEvent.BACK_IN_STOCK,
                inventoryId,
                sku,
                variantId,
                productId,
                0,
                newStock,
                0,
                "Item is back in stock with " + newStock + " units",
                null,
                java.time.Instant.now()
        );
        sendEvent(sku, event);
        log.info("BACK IN STOCK: SKU {} now has {} units", sku, newStock);
    }

    /**
     * Send an event to Kafka.
     * Uses SKU as the message key to ensure ordering per SKU.
     *
     * @param sku Message key (ensures same partition for same SKU)
     * @param event The inventory event to send
     */
    private void sendEvent(String sku, InventoryEvent event) {
        log.debug("Publishing {} event for SKU: {}, eventId: {}",
                event.eventType(), sku, event.eventId());

        CompletableFuture<SendResult<String, InventoryEvent>> future =
                kafkaTemplate.send(inventoryEventsTopic, sku, event);

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
                // In production, consider:
                // 1. Retry with exponential backoff
                // 2. Store failed events for later retry
                // 3. Send to dead-letter topic
            }
        });
    }
}
