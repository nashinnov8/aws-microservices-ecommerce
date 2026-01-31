package com.ecommerce.productservice.dto.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record BrandRequest(
        @NotBlank(message = "Brand name is required")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        @URL
        String logoUrl,

        @URL
        String websiteUrl
) {
}
