package com.ecommerce.orderservice.dto.order;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for a single order item.
 *
 * @param sku SKU of the product variant
 * @param productId Product UUID from product-service
 * @param variantId Variant UUID from product-service
 * @param productName Product name (denormalized)
 * @param variantName Variant name (denormalized, e.g., "Red / XL")
 * @param quantity Quantity to order
 * @param unitPrice Price per unit
 */
public record OrderItemRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Variant ID is required")
        UUID variantId,

        @NotBlank(message = "Product name is required")
        String productName,

        String variantName,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", message = "Unit price cannot be negative")
        BigDecimal unitPrice
) {}