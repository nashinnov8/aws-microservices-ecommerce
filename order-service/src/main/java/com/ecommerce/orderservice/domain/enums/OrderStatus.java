package com.ecommerce.orderservice.domain.enums;

/**
 * Order lifecycle status enum.
 * Flow:
 *   PENDING → STOCK_RESERVED → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
 *                                        → CANCELLED
 *           → FAILED (if stock reservation fails)
 *   Any non-terminal → CANCELLED (with reason)
 */
public enum OrderStatus {
    // Order just created, awaiting stock reservation
    PENDING,

    // Stock has been reserved successfully
    STOCK_RESERVED,

    // Order has been confirmed after successful payment
    CONFIRMED,

    // Order is being processed (e.g. packaging, preparing for shipment)
    PROCESSING,

    // Order has been shipped to the customer
    SHIPPED,

    // Order has been delivered to the customer
    DELIVERED,

    // Order was cancelled by the customer or system
    CANCELLED,

    // Order processing failed due to an error (e.g. payment failure, stock issue)
    FAILED;

    /**
     * Check if status is a terminal state (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }

    /**
     * Check if status allows cancellation.
     */
    public boolean isCancellable() {
        return this == PENDING || this == STOCK_RESERVED || this == CONFIRMED || this == PROCESSING;
    }
}
