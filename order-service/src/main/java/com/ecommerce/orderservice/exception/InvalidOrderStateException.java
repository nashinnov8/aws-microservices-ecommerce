package com.ecommerce.orderservice.exception;

import com.ecommerce.orderservice.domain.enums.OrderStatus;

/**
 * Exception thrown when an order operation is invalid for the current status.
 * For example, trying to cancel a DELIVERED order.
 */
public class InvalidOrderStateException extends RuntimeException {

    private final OrderStatus currentStatus;
    private final String attemptedAction;

    public InvalidOrderStateException(String message) {
        super(message);
        this.currentStatus = null;
        this.attemptedAction = null;
    }

    public InvalidOrderStateException(OrderStatus currentStatus, String attemptedAction) {
        super(String.format("Cannot %s order in status %s", attemptedAction, currentStatus));
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public String getAttemptedAction() {
        return attemptedAction;
    }
}

