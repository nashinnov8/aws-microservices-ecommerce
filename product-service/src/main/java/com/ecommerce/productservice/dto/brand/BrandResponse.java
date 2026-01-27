package com.ecommerce.productservice.dto.brand;

import com.ecommerce.productservice.domain.entity.Brand;

import java.time.Instant;
import java.util.UUID;

public record BrandResponse(
        UUID id,
        String name,
        String description,
        String logoUrl,
        String websiteUrl,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static BrandResponse fromEntity(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.getLogoUrl(),
                brand.getWebsiteUrl(),
                brand.getActive(),
                brand.getCreatedAt(),
                brand.getUpdatedAt()
        );
    }
}
