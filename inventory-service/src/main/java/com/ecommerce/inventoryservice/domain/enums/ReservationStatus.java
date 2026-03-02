package com.ecommerce.inventoryservice.domain.enums;

/**
 * Represents the status of a stock reservation.
 */
public enum ReservationStatus {
    /**
     * Reservation is currently active and holding stock
     */
    ACTIVE,

    /**
     * Reservation has been fulfilled (order completed)
     */
    FULFILLED,

    /**
     * Reservation was cancelled (order cancelled)
     */
    CANCELLED,

    /**
     * Reservation expired without being fulfilled
     */
    EXPIRED,

    /**
     * Reservation failed due to an error (e.g. database issue)
     */
    FAILED,

    /**
     * Reservation is pending and awaiting confirmation (e.g. payment pending)
     */
    PENDING,

    /**
     * Reservation is reserved.
     */
    RESERVED,

    /**
     * Reservation is released and stock is returned to available inventory.
     */
    RELEASED
}
