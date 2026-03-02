package com.ecommerce.inventoryservice.dto.warehouse;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for updating warehouse details.
 * All fields are optional - only non-null fields will be updated.
 *
 * @param name Updated name (null to keep existing)
 * @param location Updated location
 * @param address Updated street address
 * @param city Updated city
 * @param state Updated state/province
 * @param country Updated country
 * @param zipCode Updated postal code
 * @param capacity Updated capacity
 * @param isActive Set to false to deactivate warehouse
 */
public record UpdateWarehouseRequest(
        String name,
        String location,
        String address,
        String city,
        String state,
        String country,
        String zipCode,

        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity,

        Boolean isActive
) {}
