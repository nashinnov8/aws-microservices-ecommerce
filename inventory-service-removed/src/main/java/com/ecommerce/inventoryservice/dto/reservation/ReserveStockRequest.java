package com.ecommerce.inventoryservice.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for reserving stock for an order.
 *
 * IDEMPOTENCY: orderId + sku combination is used as idempotency key.
 * If a reservation already exists for this combination, the existing
 * reservation is returned instead of creating a duplicate.
 *
 * @param sku SKU of the item to reserve
 * @param orderId Order ID this reservation is for (idempotency key)
 * @param quantity Quantity to reserve
 * @param expirationMinutes How long the reservation should last (default: 15)
 */
public record ReserveStockRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotBlank(message = "Order ID is required")
        String orderId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity,

        Integer expirationMinutes
) {
    /**
     * Default expiration to 15 minutes if not specified.
     */
    public ReserveStockRequest {
        if (expirationMinutes == null || expirationMinutes <= 0) {
            expirationMinutes = 15;
        }
    }
}
