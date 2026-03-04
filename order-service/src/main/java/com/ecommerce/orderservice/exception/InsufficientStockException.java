package com.ecommerce.orderservice.exception;

/**
 * Exception thrown when inventory-service reports insufficient stock
 * for an order's items during the reservation process.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}

