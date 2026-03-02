package com.ecommerce.inventoryservice.domain.enums;

/**
 * Represents the type of operation when updating stock.
 */
public enum StockOperation {
    /**
     * Add to existing stock quantity
     */
    ADD,

    /**
     * Subtract from existing stock quantity
     */
    SUBTRACT,

    /**
     * Set stock to exact quantity (overwrite)
     */
    SET
}
