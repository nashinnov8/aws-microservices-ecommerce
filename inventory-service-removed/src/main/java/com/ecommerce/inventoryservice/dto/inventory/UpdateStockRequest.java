package com.ecommerce.inventoryservice.dto.inventory;

import com.ecommerce.inventoryservice.domain.enums.StockOperation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating stock quantity.
 *
 * @param operation The type of stock operation: ADD, SUBTRACT, or SET
 * @param quantity The quantity to add, subtract, or set.
 * @param reason Description or reason for the stock update
 * @param performedBy Identifier of who performed the operation.
 * @param warehouseId Optional warehouse identifier for multi-warehouse setups
 */
public record UpdateStockRequest(
        @NotNull(message = "Operation type is required")
        StockOperation operation,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity,

        String reason,

        String performedBy,

        String warehouseId
) {
    /**
     *  Constructor with default performedBy value.
     */
    public UpdateStockRequest {
        if (performedBy == null || performedBy.isBlank()) {
            performedBy = "SYSTEM";
        }
    }
}
