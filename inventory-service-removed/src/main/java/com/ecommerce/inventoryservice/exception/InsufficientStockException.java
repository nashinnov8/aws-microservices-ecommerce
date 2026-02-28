package com.ecommerce.inventoryservice.exception;

/**
 * Exception thrown when there is insufficient stock available for an operation.
 * This can occur when:
 * - Attempting to reserve more stock than available
 * - Attempting to subtract more stock than exists
 * - Attempting to transfer more stock than warehouse holds
 */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(String message) {
        super(message);
        this.sku = null;
        this.requestedQuantity = 0;
        this.availableQuantity = 0;
    }

    public InsufficientStockException(String sku, int requestedQuantity, int availableQuantity) {
        super(String.format("Insufficient stock for SKU: %s. Requested: %d, Available: %d",
                sku, requestedQuantity, availableQuantity));
        this.sku = sku;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public String getSku() {
        return sku;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getShortfall() {
        return requestedQuantity - availableQuantity;
    }
}
