package com.ecommerce.orderservice.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order line item entity.
 * Stores a snapshot of the product/variant at the time of ordering.
 * Denormalized fields (productName, variantName, unitPrice) ensure
 * order history remains accurate even if products change later.
 */
@Entity
@Table(name = "order_items",
        indexes = {
                @Index(name = "idx_oi_order_id", columnList = "order_id"),
                @Index(name = "idx_oi_sku", columnList = "sku"),
                @Index(name = "idx_oi_product_id", columnList = "productId")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * SKU — primary business identifier for inventory operations.
     */
    @NotBlank(message = "SKU is required")
    @Column(nullable = false, length = 100)
    private String sku;

    /**
     * Product UUID from product-service.
     */
    @NotNull(message = "Product ID is required")
    @Column(nullable = false)
    private UUID productId;

    /**
     * Variant UUID from product-service.
     */
    @NotNull(message = "Variant ID is required")
    @Column(nullable = false)
    private UUID variantId;

    /**
     * Denormalized product name at time of order.
     */
    @NotBlank(message = "Product name is required")
    @Column(nullable = false, length = 255)
    private String productName;

    /**
     * Denormalized variant name at time of order (e.g., "Red / XL").
     */
    @Column(length = 255)
    private String variantName;

    /**
     * Quantity ordered.
     */
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private int quantity;

    /**
     * Price per unit at time of order.
     */
    @NotNull(message = "Unit price is required")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Subtotal (quantity * unitPrice).
     */
    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /**
     * Constructor for creating a new order item.
     */
    public OrderItem(String sku, UUID productId, UUID variantId,
                     String productName, String variantName,
                     int quantity, BigDecimal unitPrice) {
        this.sku = sku;
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.variantName = variantName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}