package com.ecommerce.productservice.dto.producvariant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductVariantRequest(
        @NotBlank(message = "Variant SKU is required")
        @Size(max = 100)
        String variantSku,

        @Size(max = 50)
        String size,

        @Size(max = 50)
        String color,

        @Size(max = 100)
        String material,

        BigDecimal priceAdjustment,

        String imageUrl
) {
}
