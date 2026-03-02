package com.ecommerce.productservice.dto.category;

import com.ecommerce.productservice.domain.entity.Category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        String imageUrl,
        Boolean isActive,
        UUID parentId,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl(),
                category.getIsActive(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
