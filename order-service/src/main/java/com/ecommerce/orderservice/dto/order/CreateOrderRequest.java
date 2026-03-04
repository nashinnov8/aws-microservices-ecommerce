package com.ecommerce.orderservice.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for creating a new order.
 *
 * @param items List of order items (at least 1)
 * @param shippingAddressLine1 Shipping address line 1
 * @param shippingAddressLine2 Shipping address line 2 (optional)
 * @param shippingCity Shipping city
 * @param shippingState Shipping state/province
 * @param shippingZipCode Shipping zip/postal code
 * @param shippingCountry Shipping country
 * @param currency Currency code (default USD)
 * @param notes Customer notes (optional)
 */
public record CreateOrderRequest(
        @NotEmpty(message = "Order must have at least one item")
        @Valid
        List<OrderItemRequest> items,

        @NotBlank(message = "Shipping address is required")
        String shippingAddressLine1,

        String shippingAddressLine2,

        @NotBlank(message = "City is required")
        String shippingCity,

        @NotBlank(message = "State is required")
        String shippingState,

        @NotBlank(message = "Zip code is required")
        String shippingZipCode,

        @NotBlank(message = "Country is required")
        String shippingCountry,

        @Size(max = 3, message = "Currency code must be 3 characters")
        String currency,

        String notes
) {
}
