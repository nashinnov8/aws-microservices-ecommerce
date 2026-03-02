package com.ecommerce.inventoryservice.dto.warehouse;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for warehouse information.
 *
 * @param id Warehouse UUID
 * @param warehouseCode Unique identifier code
 * @param name Human-readable name
 * @param location General location description
 * @param address Full address info
 * @param capacity Maximum capacity
 * @param currentUtilization Current stock units in warehouse
 * @param utilizationPercentage currentUtilization / capacity * 100
 * @param isActive Whether warehouse is active
 * @param createdAt When warehouse was created
 * @param updatedAt Last update timestamp
 */
public record WarehouseResponse(
        UUID id,
        String warehouseCode,
        String name,
        String location,
        WarehouseAddress address,
        int capacity,
        int currentUtilization,
        double utilizationPercentage,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Nested address information.
     */
    public record WarehouseAddress(
            String street,
            String city,
            String state,
            String country,
            String zipCode
    ) {}
}
