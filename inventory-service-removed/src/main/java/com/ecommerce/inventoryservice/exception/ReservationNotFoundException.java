package com.ecommerce.inventoryservice.exception;

import java.util.UUID;

/**
 * Exception thrown when a stock reservation cannot be found.
 * This can occur when looking up by:
 * - Reservation UUID
 * - Order ID
 */
public class ReservationNotFoundException extends RuntimeException {

    private final UUID reservationId;
    private final String orderId;

    public ReservationNotFoundException(String message) {
        super(message);
        this.reservationId = null;
        this.orderId = null;
    }

    public static ReservationNotFoundException byId(UUID reservationId) {
        return new ReservationNotFoundException(
                "Reservation not found with ID: " + reservationId);
    }

    public static ReservationNotFoundException byOrderId(String orderId) {
        return new ReservationNotFoundException(
                "Reservation not found for order ID: " + orderId);
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getOrderId() {
        return orderId;
    }
}
