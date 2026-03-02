package com.ecommerce.productservice.dto.producvariant;

import com.ecommerce.productservice.domain.entity.ProductVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        UUID productId,
        String variantSku,
        String size,
        String color,
        String material,
        BigDecimal priceAdjustment,
        BigDecimal finalPrice,
        String imageUrl,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductVariantResponse fromEntity(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getProduct() != null ? variant.getProduct().getId() : null,
                variant.getVariantSku(),
                variant.getSize(),
                variant.getColor(),
                variant.getMaterial(),
                variant.getPriceAdjustment(),
                variant.getFinalPrice(),
                variant.getImageUrl(),
                variant.getStatus() != null ? variant.getStatus().name() : null,
                variant.getCreatedAt(),
                variant.getUpdatedAt()
        );
    }
}
