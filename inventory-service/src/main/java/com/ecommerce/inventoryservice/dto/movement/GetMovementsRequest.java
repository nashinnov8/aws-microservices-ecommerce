package com.ecommerce.inventoryservice.dto.movement;

import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for filtering stock movement history.
 * All fields are optional - used as query parameters.
 *
 * @param inventoryId Filter by inventory item
 * @param sku Filter by SKU
 * @param warehouseId Filter by warehouse
 * @param movementType Filter by movement type (RECEIVE, SHIP, etc.)
 * @param startDate Filter movements after this date
 * @param endDate Filter movements before this date
 * @param referenceId Filter by external reference (e.g., order ID)
 * @param performedBy Filter by who performed the movement
 * @param page Page number (0-based)
 * @param size Page size (default 20)
 */
public record GetMovementsRequest(
        UUID inventoryId,
        String sku,
        UUID warehouseId,
        String movementType,
        Instant startDate,
        Instant endDate,
        String referenceId,
        String performedBy,
        Integer page,
        Integer size
) {
    public GetMovementsRequest {
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 20;
        if (size > 100) size = 100; // Max page size
    }
}
