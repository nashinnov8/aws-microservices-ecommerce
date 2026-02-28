package com.ecommerce.inventoryservice.dto.reservation;

import java.util.List;

/**
 * Response DTO for bulk reservation operations.
 *
 * @param success True if all items were successfully reserved
 * @param orderId The order ID
 * @param reservations List of individual reservation results
 * @param failedItems Items that could not be reserved (empty if success=true)
 */
public record BulkReservationResponse(
        boolean success,
        String orderId,
        List<ReservationResponse> reservations,
        List<FailedReservation> failedItems
) {
    /**
     * Details about a failed reservation attempt.
     *
     * @param sku SKU that failed
     * @param requestedQuantity Quantity that was requested
     * @param availableQuantity Actual available quantity
     * @param reason Human-readable reason for failure
     */
    public record FailedReservation(
            String sku,
            int requestedQuantity,
            int availableQuantity,
            String reason
    ) {}
}
