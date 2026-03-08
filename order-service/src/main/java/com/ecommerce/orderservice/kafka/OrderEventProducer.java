package com.ecommerce.orderservice.kafka;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.dto.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Kafka producer for publishing order events to other services.
 * <p>
 * Events published:
 * - ORDER_COMPLETED → inventory-service fulfills reservations
 * - ORDER_CANCELLED → inventory-service releases reservations
 * - ORDER_PAYMENT_FAILED → inventory-service releases reservations
 * <p>
 * Uses orderId as the message key for partition consistency,
 * ensuring all events for the same order go to the same partition.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${kafka.topics.order-events:order-events}")
    private String orderEventsTopic;

    /**
     * Publish ORDER_CREATED event.
     * Called when a new order is created and stock is reserved.
     */
    public void publishOrderCreated(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderCreated(
                order.getId().toString(),
                items
        );
        sendEvent(order.getId().toString(), event);
    }

    /**
     * Publish ORDER_COMPLETED event.
     * Called when order is confirmed/completed (payment success).
     * Inventory-service will fulfill the reservations.
     */
    public void publishOrderCompleted(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderCompleted(
                order.getId().toString(),
                items
        );
        sendEvent(order.getId().toString(), event);
    }

    /**
     * Publish ORDER_CANCELLED event.
     * Called when order is canceled by customer or admin.
     * Inventory-service will release the reservations.
     */
    public void publishOrderCancelled(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderCancelled(
                order.getId().toString(),
                items
        );
        sendEvent(order.getId().toString(), event);
    }

    /**
     * Publish ORDER_PAYMENT_FAILED event.
     * Called when payment processing fails.
     * Inventory-service will release the reservations.
     */
    public void publishOrderPaymentFailed(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderPaymentFailed(order.getId().toString(), items);
        sendEvent(order.getId().toString(), event);
    }

    private List<OrderEvent.OrderEventItem> mapOrderItems(Order order) {
        return order.getItems().stream()
                .map(orderItem -> new OrderEvent.OrderEventItem(
                        orderItem.getSku(),
                        orderItem.getVariantId(),
                        orderItem.getQuantity()
                ))
                .collect(Collectors.toList());
    }

    private void sendEvent(String orderId, OrderEvent event) {
        log.debug("Publishing {} event for order: {}, orderId: {}", event.eventType(), orderId, event);
        CompletableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(orderEventsTopic, orderId, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published {} event for order: {} to partition {} with offset {}",
                        event.eventType(),
                        orderId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish {} event for order: {}, error: {}",
                        event.eventType(),
                        orderId,
                        ex.getMessage(), ex);
            }
        });
    }

}
