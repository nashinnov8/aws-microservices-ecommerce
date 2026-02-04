package com.ecommerce.productservice.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

public record CategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        @URL
        String imageUrl,

        UUID parentId
) {
}
