package com.ecommerce.inventoryservice.exception;

/**
 * Exception thrown when an invalid stock operation is attempted.
 * This can occur when:
 * - Attempting to update stock with an invalid operation type
 * - Attempting a transfer with same source and destination warehouse
 * - Attempting to reserve already fulfilled/cancelled reservation
 */
public class InvalidStockOperationException extends RuntimeException {

    public InvalidStockOperationException(String message) {
        super(message);
    }

    public static InvalidStockOperationException sameWarehouseTransfer() {
        return new InvalidStockOperationException(
                "Cannot transfer stock to the same warehouse");
    }

    public static InvalidStockOperationException reservationAlreadyProcessed(String status) {
        return new InvalidStockOperationException(
                "Reservation has already been processed with status: " + status);
    }
}
