package com.ecommerce.inventoryservice.domain.enums;

/**
 * Represents the type of stock movement in the inventory system.
 */
public enum MovementType {
    /**
     * Stock received from supplier/warehouse
     */
    RECEIVE,

    /**
     * Stock shipped out for orders
     */
    SHIP,

    /**
     * Manual stock adjustment (corrections, damages, etc.)
     */
    ADJUST,

    /**
     * Stock reserved for pending order
     */
    RESERVE,

    /**
     * Reserved stock released back to available
     */
    RELEASE,

    /**
     * Stock transferred in from another warehouse
     */
    TRANSFER_IN,

    /**
     * Stock transferred out to another warehouse
     */
    TRANSFER_OUT
}
