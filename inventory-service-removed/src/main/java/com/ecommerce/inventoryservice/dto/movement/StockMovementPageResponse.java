package com.ecommerce.inventoryservice.dto.movement;

import java.util.List;

/**
 * Paginated response for stock movement history.
 *
 * @param content List of movements
 * @param pageNumber Current page number (0-based)
 * @param pageSize Items per page
 * @param totalElements Total number of movements
 * @param totalPages Total number of pages
 * @param first True if this is the first page
 * @param last True if this is the last page
 */
public record StockMovementPageResponse(
        List<StockMovementResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {}
