package com.ecommerce.orderservice.exception;

import java.util.UUID;

/**
 * Exception thrown when an order cannot be found.
 */
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;
    private final String orderNumber;

    public OrderNotFoundException(String message) {
        super(message);
        this.orderId = null;
        this.orderNumber = null;
    }

    public static OrderNotFoundException byId(UUID orderId) {
        return new OrderNotFoundException("Order not found with ID: " + orderId);
    }

    public static OrderNotFoundException byOrderNumber(String orderNumber) {
        return new OrderNotFoundException("Order not found with order number: " + orderNumber);
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }
}
