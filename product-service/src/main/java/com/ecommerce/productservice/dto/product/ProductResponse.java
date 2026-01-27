package com.ecommerce.productservice.dto.product;

import com.ecommerce.productservice.domain.entity.Product;
import com.ecommerce.productservice.dto.producvariant.ProductVariantResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        String baseSku,
        BigDecimal price,
        String brand,
        String imageUrl,
        String status,
        String categoryName,
        List<ProductVariantResponse> variants,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse fromEntity(Product product) {
        List<ProductVariantResponse> variantResponses = product.getVariants().stream()
                .map(ProductVariantResponse::fromEntity)
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getBaseSku(),
                product.getPrice(),
                product.getBrand() != null ? product.getBrand().getName() : null,
                product.getImageUrl(),
                product.getStatus().name(),
                product.getCategory() != null ? product.getCategory().getName() : null,
                variantResponses,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
    public static List<ProductResponse> fromEntityList(List<Product> products) {
        return products.stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

}
