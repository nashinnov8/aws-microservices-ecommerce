package com.ecommerce.orderservice.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderEvent(
        String eventId,
        String eventType,
        String orderId,
        List<OrderEventItem> items,
        Instant timestamp
) {
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_PAYMENT_FAILED = "ORDER_PAYMENT_FAILED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";

    public record OrderEventItem(
            String sku,
            UUID variantId,
            int quantity
    ) {}

    /**
     * Factory for ORDER_CREATED event.
     */
    public static OrderEvent orderCreated(String orderId, List<OrderEventItem> orderItems) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_CREATED,
                orderId,
                orderItems,
                Instant.now()
        );
    }
    /**
     * Factory for ORDER_COMPLETED event.
     */
    public static OrderEvent orderCompleted(String orderId, List<OrderEventItem> orderItems) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_COMPLETED,
                orderId,
                orderItems,
                Instant.now()
        );
    }

    /**
     * Factory for ORDER_CANCELLED event.
     */
    public static OrderEvent orderCancelled(String orderId, List<OrderEventItem> items) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_CANCELLED,
                orderId,
                items,
                Instant.now()
        );
    }

    /**
     * Factory for ORDER_PAYMENT_FAILED event.
     */
    public static OrderEvent orderPaymentFailed(String orderId, List<OrderEventItem> items) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_PAYMENT_FAILED,
                orderId,
                items,
                Instant.now()
        );
    }
}
