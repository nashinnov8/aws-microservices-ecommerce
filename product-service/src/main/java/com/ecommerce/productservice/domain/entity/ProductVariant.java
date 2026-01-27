package com.ecommerce.productservice.domain.entity;

import com.ecommerce.productservice.domain.enums.ProductStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
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

    // Constructors
    public ProductVariant() {}

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

    // Getters and setters
    public String getVariantSku() { return variantSku; }
    public void setVariantSku(String variantSku) { this.variantSku = variantSku; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public BigDecimal getPriceAdjustment() { return priceAdjustment; }
    public void setPriceAdjustment(BigDecimal priceAdjustment) { this.priceAdjustment = priceAdjustment; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
}
