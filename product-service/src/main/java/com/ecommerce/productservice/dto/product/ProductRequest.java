package com.ecommerce.productservice.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 255)
        String name,

        @Size(max = 2000)
        String description,

        @NotBlank(message = "Base SKU is required")
        @Size(max = 100)
        String baseSku,

        @Positive()
        BigDecimal price,

        @URL
        String imageUrl,

        UUID categoryId,

        UUID brandId
) {
}
