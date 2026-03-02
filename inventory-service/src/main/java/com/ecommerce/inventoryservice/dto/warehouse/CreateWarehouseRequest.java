package com.ecommerce.inventoryservice.dto.warehouse;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new warehouse.
 *
 * @param warehouseCode Unique identifier code (e.g., "WH-SYD-01")
 * @param name Human-readable name
 * @param location General location description
 * @param address Street address
 * @param city City name
 * @param state State/Province
 * @param country Country name
 * @param zipCode Postal code
 * @param capacity Maximum units the warehouse can hold
 */
public record CreateWarehouseRequest(
        @NotBlank(message = "Warehouse code is required")
        String warehouseCode,

        @NotBlank(message = "Warehouse name is required")
        String name,

        String location,
        String address,
        String city,
        String state,
        String country,
        String zipCode,

        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity
) {
    public CreateWarehouseRequest {
        if (capacity == null) capacity = 10000;
    }
}
