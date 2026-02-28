package com.ecommerce.inventoryservice.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Core entity for tracking inventory at SKU/variant level.
 * Each product variant (size, color combination) has its own inventory record.
 * SKU is the primary business key used for all operations.
 */
@Entity
@Table(name = "inventory", indexes = {
    @Index(name = "idx_inventory_sku", columnList = "sku"),
    @Index(name = "idx_inventory_variant_id", columnList = "variantId"),
    @Index(name = "idx_inventory_product_id", columnList = "productId"),
    @Index(name = "idx_inventory_is_active", columnList = "isActive")
})
@Getter
@Setter
@NoArgsConstructor
public class Inventory extends BaseEntity {

    /**
     * SKU - THE primary business identifier.
     * Matches ProductVariant.variantSku in product-service.
     * Used for warehouse scanning, order fulfillment, and all operations.
     */
    @NotBlank(message = "SKU is required")
    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    /**
     * References ProductVariant.id in product-service.
     * Used for cross-service references.
     */
    @NotNull(message = "Variant ID is required")
    @Column(nullable = false, unique = true)
    private UUID variantId;

    /**
     * References Product.id in product-service.
     * Used for grouping and reporting.
     */
    @NotNull(message = "Product ID is required")
    @Column(nullable = false)
    private UUID productId;

    /**
     * Denormalized product name for display purposes.
     * Synced via Kafka events from product-service.
     */
    @Column(length = 255)
    private String productName;

    /**
     * Denormalized variant name for display (e.g., "Red / XL").
     * Synced via Kafka events from product-service.
     */
    @Column(length = 255)
    private String variantName;

    /**
     * Available stock quantity that can be sold.
     * This is the quantity not reserved for any orders.
     */
    @Min(value = 0, message = "Available stock cannot be negative")
    @Column(nullable = false)
    private int availableStock = 0;

    /**
     * Reserved stock quantity for pending orders.
     * Stock is reserved when an order is placed and released when fulfilled or cancelled.
     */
    @Min(value = 0, message = "Reserved stock cannot be negative")
    @Column(nullable = false)
    private int reservedStock = 0;

    /**
     * Minimum stock level threshold for alerts.
     */
    @Min(value = 0, message = "Min stock level cannot be negative")
    @Column(nullable = false)
    private int minStockLevel = 10;

    /**
     * Maximum stock level for warehouse capacity planning.
     */
    @Min(value = 0, message = "Max stock level cannot be negative")
    @Column(nullable = false)
    private int maxStockLevel = 1000;

    /**
     * Reorder point - when available stock falls to or below this level,
     * a low stock alert should be triggered.
     */
    @Min(value = 0, message = "Reorder point cannot be negative")
    @Column(nullable = false)
    private int reorderPoint = 20;

    /**
     * Whether this inventory record is active.
     * Set to false when variant is deleted (soft delete).
     */
    @Column(nullable = false)
    private boolean isActive = true;

    /**
     * Constructor for creating new inventory record.
     */
    public Inventory(String sku, UUID variantId, UUID productId, String productName, String variantName) {
        this.sku = sku;
        this.variantId = variantId;
        this.productId = productId;
        this.productName = productName;
        this.variantName = variantName;
    }

    /**
     * Get total stock (available + reserved).
     */
    public int getTotalStock() {
        return availableStock + reservedStock;
    }

    /**
     * Check if stock is below reorder point.
     */
    public boolean isLowStock() {
        return availableStock <= reorderPoint;
    }

    /**
     * Check if stock is at or below minimum level.
     */
    public boolean isBelowMinimum() {
        return availableStock <= minStockLevel;
    }
}
