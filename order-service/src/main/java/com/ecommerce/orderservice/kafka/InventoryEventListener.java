package com.ecommerce.orderservice.kafka;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.domain.repository.OrderRepository;
import com.ecommerce.orderservice.dto.event.InventoryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "inventory-events",
            groupId = "order-service",
            containerFactory = "inventoryEventListenerContainerFactory"
    )
    public void handleInventoryEvent(String message) {
        try {
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);
            log.info("Received inventory event: {} for SKU: {}, referenceId: {}",
                    event.eventType(), event.sku(), event.referenceId());

            switch (event.eventType()) {
                case InventoryEvent.STOCK_RESERVED -> handleStockReserved(event);
                case InventoryEvent.STOCK_RELEASED -> handleStockReleased(event);
                case InventoryEvent.OUT_OF_STOCK -> handleOutOfStock(event);
                case InventoryEvent.BACK_IN_STOCK -> handleBackInStock(event);
                default -> log.debug("Ignoring inventory event type: {}", event.eventType());
            }

        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", message, e);
            // Let the error handler manage retries and dead-lettering
            throw new RuntimeException("Error processing inventory event", e);
        }
    }

    /**
     * SAGA SUCCESS -> Stock was reserved for this order
     * Status will be changed from PENDING to STOCK_RESERVED
     * <p>
     * Note: inventory-service publishes one STOCK_RESERVED event per SKU.
     * For multi-item orders, we receive multiple events. The first one transitions the order;
     * subsequent ones are idempotent.
     */
    private void handleStockReserved(InventoryEvent event) {
        log.info("Stock reserved for order: {}, SKU: {}, quantity: {}",
                event.referenceId(), event.sku(), event.newQuantity());
        // Could update order status to STOCK_RESERVED if tracking per-item status
        String orderId = event.referenceId();

        if (orderId == null) {
            log.debug("No referenceId in STOCK_RESERVED event, skipping order update");
            return;
        }

        log.info("Stock reserved for order: {}, SKU: {}, quantity: {}",
                orderId, event.sku(), event.newQuantity());

        Optional<Order> orderOpt = findOrder(orderId);

        if (orderOpt.isEmpty()) {
            return;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PENDING)  {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            orderRepository.save(order);
            log.info("Order {} status updated to STOCK_RESERVED", orderId);
        } else {
            log.debug("Order {} already in status {}, ignoring STOCK_RESERVED event",
                    orderId, order.getStatus());
        }
    }

    /**
     * SAGA FAILURE -> Stock reservation failed for this order
     * Status will be changed from PENDING to FAILED
     * <p>
     * Note: inventory-service publishes one STOCK_RESERVATION_FAILED event per SKU.
     * For multi-item orders, we receive multiple events. The first one transitions the order;
     * subsequent ones are idempotent.
     */
    private void handleStockReservedFailed(InventoryEvent event) {
        log.warn("Stock reservation FAILED for order: {}, SKU: {}, quantity: {}, reason: {}",
                event.referenceId(), event.sku(), event.newQuantity(), event.reason());
        // Could update order status to FAILED if tracking per-item status
         String orderId = event.referenceId();

        if (orderId == null) {
            log.debug("No referenceId in STOCK_RESERVATION_FAILED event, skipping order update");
            return;
        }

        log.warn("Stock reservation FAILED for order: {}, SKU: {}, quantity: {}, reason: {}",
                orderId, event.sku(), event.newQuantity(), event.reason());

        Optional<Order> orderOpt = findOrder(orderId);

        if (orderOpt.isEmpty()) {
            return;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PENDING)  {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            log.info("Order {} status updated to FAILED", orderId);
        } else {
            log.debug("Order {} already in status {}, ignoring STOCK_RESERVATION_FAILED event",
                    orderId, order.getStatus());
        }
    }

    private Optional<Order> findOrder(String orderId) {
        try {
            UUID orderUuid = UUID.fromString(orderId);
            Optional<Order> orderOpt = orderRepository.findByIdForUpdate(orderUuid);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for STOCK_RESERVED event: {}", orderId);
                return Optional.empty();
            }
            return orderOpt;
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid orderId in inventory event: {}", orderId, exception);
            return Optional.empty();
        }
    }

    /**
     * Handle stock released event.
     * Stock was released — reservation was cancelled/expired.
     */
    private void handleStockReleased(InventoryEvent event) {
        log.info("Stock released for order: {}, SKU: {}, quantity: {}, reason: {}",
                event.referenceId(), event.sku(), event.newQuantity(), event.reason());
        // Could update order status if reservation was released unexpectedly
    }

    /**
     * Handle out of stock event.
     * A product is now out of stock — could notify affected pending orders.
     */
    private void handleOutOfStock(InventoryEvent event) {
        log.warn("OUT OF STOCK: SKU {} is now out of stock", event.sku());
        // Could notify customers with pending/wishlisted orders
    }

    /**
     * Handle back in stock event.
     * A product is back in stock — could notify customers.
     */
    private void handleBackInStock(InventoryEvent event) {
        log.info("BACK IN STOCK: SKU {} now has {} units", event.sku(), event.newQuantity());
        // Could notify customers who were interested
    }
}
