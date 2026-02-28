package com.ecommerce.inventoryservice.exception;

/**
 * Exception thrown when a warehouse is at full capacity and cannot accept more stock.
 * This can occur during:
 * - Stock transfers to a warehouse
 * - Stock receiving operations
 */
public class WarehouseCapacityExceededException extends RuntimeException {

    private final String warehouseCode;
    private final int currentUtilization;
    private final int capacity;
    private final int requestedQuantity;

    public WarehouseCapacityExceededException(String message) {
        super(message);
        this.warehouseCode = null;
        this.currentUtilization = 0;
        this.capacity = 0;
        this.requestedQuantity = 0;
    }

    public WarehouseCapacityExceededException(String warehouseCode, int currentUtilization,
                                               int capacity, int requestedQuantity) {
        super(String.format(
                "Warehouse %s cannot accept %d units. Current: %d, Capacity: %d, Available space: %d",
                warehouseCode, requestedQuantity, currentUtilization, capacity,
                capacity - currentUtilization));
        this.warehouseCode = warehouseCode;
        this.currentUtilization = currentUtilization;
        this.capacity = capacity;
        this.requestedQuantity = requestedQuantity;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public int getCurrentUtilization() {
        return currentUtilization;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableSpace() {
        return capacity - currentUtilization;
    }
}
