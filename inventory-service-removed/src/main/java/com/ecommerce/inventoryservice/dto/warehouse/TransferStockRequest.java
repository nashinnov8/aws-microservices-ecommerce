package com.ecommerce.inventoryservice.dto.warehouse;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for transferring stock between warehouses.
 *
 * @param sku SKU of the item to transfer
 * @param fromWarehouseId Source warehouse UUID
 * @param toWarehouseId Destination warehouse UUID
 * @param quantity Quantity to transfer
 * @param reason Reason for the transfer
 * @param performedBy User initiating the transfer
 */
public record TransferStockRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Source warehouse ID is required")
        UUID fromWarehouseId,

        @NotNull(message = "Destination warehouse ID is required")
        UUID toWarehouseId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity,

        String reason,
        String performedBy
) {}
