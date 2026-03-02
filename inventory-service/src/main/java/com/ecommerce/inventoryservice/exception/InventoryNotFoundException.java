package com.ecommerce.inventoryservice.exception;

import java.util.UUID;

/**
 * Exception thrown when an inventory record cannot be found.
 * This can occur when looking up by:
 * - SKU (primary business key)
 * - Inventory UUID
 * - Variant ID
 */
public class InventoryNotFoundException extends RuntimeException {

    private final String sku;
    private final UUID inventoryId;
    private final UUID variantId;

    public InventoryNotFoundException(String message) {
        super(message);
        this.sku = null;
        this.inventoryId = null;
        this.variantId = null;
    }

    public static InventoryNotFoundException bySku(String sku) {
        InventoryNotFoundException ex = new InventoryNotFoundException(
                "Inventory not found for SKU: " + sku);
        return ex;
    }

    public static InventoryNotFoundException byId(UUID inventoryId) {
        return new InventoryNotFoundException(
                "Inventory not found with ID: " + inventoryId);
    }

    public static InventoryNotFoundException byVariantId(UUID variantId) {
        return new InventoryNotFoundException(
                "Inventory not found for variant ID: " + variantId);
    }

    public String getSku() {
        return sku;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public UUID getVariantId() {
        return variantId;
    }
}
