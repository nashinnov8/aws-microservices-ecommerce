package com.ecommerce.inventoryservice.dto.reservation;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating a reservation (fulfill or release).
 *
 * @param action The action to perform: FULFILL or RELEASE
 * @param reason Optional reason for the action (for audit)
 */
public record UpdateReservationRequest(
        @NotNull(message = "Action is required")
        ReservationAction action,

        String reason
) {
    /**
     * Possible actions for reservation updates.
     */
    public enum ReservationAction {
        /**
         * Mark as fulfilled (order completed, stock leaves system).
         */
        FULFILL,

        /**
         * Release reservation (order cancelled, stock returns to available).
         */
        RELEASE
    }
}
