package com.ecommerce.inventoryservice.exception;

/**
 * Exception thrown when attempting to create a duplicate inventory record.
 * This occurs when:
 * - An inventory record already exists for a given SKU
 * - An inventory record already exists for a given variant ID
 *
 * Note: In most cases, duplicate creation attempts should be handled
 * idempotently (returning the existing record) rather than throwing this exception.
 * This exception is for cases where duplicate creation is explicitly not allowed.
 */
public class DuplicateInventoryException extends RuntimeException {

    private final String sku;

    public DuplicateInventoryException(String message) {
        super(message);
        this.sku = null;
    }

    public DuplicateInventoryException(String message, String sku) {
        super(message);
        this.sku = sku;
    }

    public static DuplicateInventoryException bySku(String sku) {
        return new DuplicateInventoryException(
                "Inventory already exists for SKU: " + sku, sku);
    }

    public String getSku() {
        return sku;
    }
}
