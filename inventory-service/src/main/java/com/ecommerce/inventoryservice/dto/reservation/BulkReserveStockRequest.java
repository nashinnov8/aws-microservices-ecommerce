package com.ecommerce.inventoryservice.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for reserving multiple items for an order.
 * Used for cart checkout where multiple SKUs need to be reserved atomically.
 *
 * @param orderId Order ID for all reservations
 * @param items List of items to reserve
 * @param expirationMinutes Expiration time for all reservations
 */
public record BulkReserveStockRequest(
        @NotBlank(message = "Order ID is required")
        String orderId,

        @NotEmpty(message = "Items list cannot be empty")
        @Valid
        List<ReserveItem> items,

        Integer expirationMinutes
) {
    /**
     * Individual item in bulk reservation request.
     *
     * @param sku SKU to reserve
     * @param quantity Quantity to reserve
     */
    public record ReserveItem(
            @NotBlank(message = "SKU is required")
            String sku,

            @Min(value = 1, message = "Quantity must be at least 1")
            int quantity
    ) {}

    /**
     * Default expiration to 15 minutes if not specified.
     */
    public BulkReserveStockRequest {
        if (expirationMinutes == null || expirationMinutes <= 0) {
            expirationMinutes = 15;
        }
    }
}
