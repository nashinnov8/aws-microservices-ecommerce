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
    EXPIRED
}
