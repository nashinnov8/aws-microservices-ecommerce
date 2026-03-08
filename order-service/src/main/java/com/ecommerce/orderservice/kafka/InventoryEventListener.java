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
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * SAGA RESPONSE HANDLER — consumes inventory events from inventory-service.
 *
 * This listener is CRITICAL for the saga pattern. It receives the async response
 * from inventory-service after ORDER_CREATED was published:
 *
 * - STOCK_RESERVED            → Order PENDING → STOCK_RESERVED (saga success)
 * - STOCK_RESERVATION_FAILED  → Order PENDING → FAILED (saga failure)
 * - STOCK_RELEASED            → Logged (compensation confirmed)
 */
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
    @Transactional
    public void handleInventoryEvent(String message) {
        try {
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);
            log.info("Received inventory event: {} for SKU: {}, referenceId (orderId): {}",
                    event.eventType(), event.sku(), event.referenceId());

            switch (event.eventType()) {
                case InventoryEvent.STOCK_RESERVED -> handleStockReserved(event);
                case InventoryEvent.STOCK_RESERVATION_FAILED -> handleStockReservationFailed(event);
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
     * SAGA SUCCESS — Stock was reserved for this order.
     * Transition: PENDING → STOCK_RESERVED.
     *
     * Note: inventory-service publishes one STOCK_RESERVED event per SKU.
     * For multi-item orders, we receive multiple events. The first one
     * transitions the order; subsequent ones are idempotent.
     */
    private void handleStockReserved(InventoryEvent event) {
        String orderId = event.referenceId();
        if (orderId == null) {
            log.debug("STOCK_RESERVED event without referenceId (not order-related), skipping");
            return;
        }

        log.info("Stock reserved for order: {}, SKU: {}, quantity: {}",
                orderId, event.sku(), event.newQuantity());

        Optional<Order> orderOpt = findOrder(orderId);
        if (orderOpt.isEmpty()) return;

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            orderRepository.save(order);
            log.info("Order {} transitioned PENDING → STOCK_RESERVED", order.getOrderNumber());
        } else {
            log.debug("Order {} already in status {}, ignoring STOCK_RESERVED",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    /**
     * SAGA FAILURE — Stock reservation failed for this order.
     * Transition: PENDING → FAILED.
     *
     * Inventory-service was unable to reserve stock for one or more items.
     * The order cannot proceed.
     */
    private void handleStockReservationFailed(InventoryEvent event) {
        String orderId = event.referenceId();
        if (orderId == null) {
            log.warn("STOCK_RESERVATION_FAILED event without referenceId, skipping");
            return;
        }

        log.warn("Stock reservation FAILED for order: {}, SKU: {}, reason: {}",
                orderId, event.sku(), event.reason());

        Optional<Order> orderOpt = findOrder(orderId);
        if (orderOpt.isEmpty()) return;

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.FAILED);
            order.setCancelledReason("Stock reservation failed: " + event.reason());
            orderRepository.save(order);
            log.info("Order {} transitioned PENDING → FAILED due to stock reservation failure",
                    order.getOrderNumber());
        } else {
            log.debug("Order {} already in status {}, ignoring STOCK_RESERVATION_FAILED",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    /**
     * Helper to find order by referenceId (orderId string).
     * Handles UUID parsing and not-found gracefully.
     */
    private Optional<Order> findOrder(String orderId) {
        try {
            UUID uuid = UUID.fromString(orderId);
            Optional<Order> orderOpt = orderRepository.findByIdForUpdate(uuid);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for referenceId: {}", orderId);
            }
            return orderOpt;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in referenceId: {}", orderId);
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
