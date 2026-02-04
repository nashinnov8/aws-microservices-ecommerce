package com.ecommerce.productservice.domain.entity;

import com.ecommerce.productservice.domain.enums.ProductStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant extends BaseEntity {

    @NotBlank(message = "Variant SKU is required")
    @Column(unique = true, nullable = false)
    private String variantSku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String size;

    private String color;

    private String material;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceAdjustment;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.ACTIVE;

    public ProductVariant(String variantSku, Product product) {
        this.variantSku = variantSku;
        this.product = product;
    }

    // Calculate final price
    public BigDecimal getFinalPrice() {
        BigDecimal base = product.getStatus() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal adjustment = priceAdjustment != null ? priceAdjustment : BigDecimal.ZERO;
        return base.add(adjustment);
    }
}
