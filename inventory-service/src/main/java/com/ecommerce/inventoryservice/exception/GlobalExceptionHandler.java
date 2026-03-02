package com.ecommerce.inventoryservice.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import response.ApiResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Inventory Service.
 * Provides consistent error responses using ApiResponse format.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== Business Exception Handlers ====================

    /**
     * Handle inventory not found exceptions.
     * Returns 404 Not Found.
     */
    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInventoryNotFoundException(InventoryNotFoundException ex) {
        log.warn("Inventory not found: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("INVENTORY_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle warehouse not found exceptions.
     * Returns 404 Not Found.
     */
    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWarehouseNotFoundException(WarehouseNotFoundException ex) {
        log.warn("Warehouse not found: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("WAREHOUSE_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle reservation not found exceptions.
     * Returns 404 Not Found.
     */
    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReservationNotFoundException(ReservationNotFoundException ex) {
        log.warn("Reservation not found: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("RESERVATION_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle insufficient stock exceptions.
     * Returns 409 Conflict (stock state conflict).
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleInsufficientStockException(
            InsufficientStockException ex) {
        log.warn("Insufficient stock: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        if (ex.getSku() != null) {
            details.put("sku", ex.getSku());
            details.put("requestedQuantity", ex.getRequestedQuantity());
            details.put("availableQuantity", ex.getAvailableQuantity());
            details.put("shortfall", ex.getShortfall());
        }

        ApiResponse<Map<String, Object>> response = ApiResponse.success(
                "INSUFFICIENT_STOCK", ex.getMessage(), details);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle duplicate inventory exceptions.
     * Returns 409 Conflict.
     */
    @ExceptionHandler(DuplicateInventoryException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateInventoryException(DuplicateInventoryException ex) {
        log.warn("Duplicate inventory: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("DUPLICATE_INVENTORY", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle warehouse capacity exceeded exceptions.
     * Returns 409 Conflict.
     */
    @ExceptionHandler(WarehouseCapacityExceededException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleWarehouseCapacityExceededException(
            WarehouseCapacityExceededException ex) {
        log.warn("Warehouse capacity exceeded: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        if (ex.getWarehouseCode() != null) {
            details.put("warehouseCode", ex.getWarehouseCode());
            details.put("currentUtilization", ex.getCurrentUtilization());
            details.put("capacity", ex.getCapacity());
            details.put("availableSpace", ex.getAvailableSpace());
            details.put("requestedQuantity", ex.getRequestedQuantity());
        }

        ApiResponse<Map<String, Object>> response = ApiResponse.success(
                "WAREHOUSE_CAPACITY_EXCEEDED", ex.getMessage(), details);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle invalid stock operation exceptions.
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(InvalidStockOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStockOperationException(
            InvalidStockOperationException ex) {
        log.warn("Invalid stock operation: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("INVALID_STOCK_OPERATION", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== Validation Exception Handlers ====================

    /**
     * Handle validation exceptions from @Valid annotations on request bodies.
     * Returns 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        String errorMessage = "Validation failed: " + errors;
        log.warn(errorMessage);
        ApiResponse<Map<String, String>> response = ApiResponse.success(
                "VALIDATION_ERROR", errorMessage, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle constraint violation exceptions from @Validated path/query parameters.
     * Returns 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (existing, replacement) -> existing
                ));

        String errorMessage = "Validation failed: " + errors;
        log.warn(errorMessage);
        ApiResponse<Map<String, String>> response = ApiResponse.success(
                "VALIDATION_ERROR", errorMessage, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== Concurrency Exception Handlers ====================

    /**
     * Handle optimistic locking failures (version conflicts).
     * Occurs when two requests try to update the same entity simultaneously.
     * Returns 409 Conflict — client should retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                "VERSION_CONFLICT",
                "Resource was modified by another request. Please retry.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle data integrity violations (unique constraint violations, FK violations).
     * Returns 409 Conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                "DATA_INTEGRITY_ERROR",
                "A data integrity constraint was violated. The record may already exist.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // ==================== Generic Exception Handlers ====================

    /**
     * Handle illegal argument exceptions.
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("BAD_REQUEST", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle illegal state exceptions.
     * Returns 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("CONFLICT", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Returns 500 Internal Server Error.
     * Logs the full stack trace for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        ApiResponse<Void> response = ApiResponse.error(
                "INTERNAL_ERROR", "An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
