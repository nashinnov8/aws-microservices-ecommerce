# Inventory Service - Detailed Implementation Guide

**For**: Developers  
**Status**: Ready for Implementation  
**Estimated Effort**: 3-4 days for Phase 1 & 2

---

## Part 1: REST Controller Implementation

### File: `InventoryController.java`

```java
package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.inventory.*;
import com.ecommerce.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for inventory operations.
 * Handles stock level queries, updates, and inventory management.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {
    
    private final InventoryService inventoryService;

    /**
     * Get stock information for a specific SKU.
     * 
     * @param sku Product SKU
     * @return Stock information
     */
    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<StockInfoResponse>> getStockBySku(@PathVariable String sku) {
        log.info("Fetching stock for SKU: {}", sku);
        StockInfoResponse response = inventoryService.getStockBySku(sku);
        return ResponseEntity.ok(
            ApiResponse.success(response, "Stock information retrieved successfully")
        );
    }

    /**
     * Get stock information for all variants of a product.
     * 
     * @param productId Product UUID
     * @return List of stock information
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<StockInfoResponse>>> getStocksByProductId(
            @PathVariable UUID productId) {
        log.info("Fetching stocks for Product ID: {}", productId);
        List<StockInfoResponse> responses = inventoryService.getStocksByProductId(productId);
        return ResponseEntity.ok(
            ApiResponse.success(responses, "Product stock information retrieved successfully")
        );
    }

    /**
     * Create new inventory for a variant.
     * Typically called from product-service via Kafka event listener.
     * Can also be called directly for manual inventory creation.
     * 
     * @param request Inventory creation request
     * @return Created inventory information
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<StockInfoResponse>> createInventory(
            @Valid @RequestBody CreateInventoryRequest request) {
        log.info("Creating inventory for SKU: {}", request.sku());
        StockInfoResponse response = inventoryService.createInventory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Inventory created successfully"));
    }

    /**
     * Update stock quantity for a SKU.
     * Supports three operations: ADD, SUBTRACT, SET.
     * 
     * Operation behavior:
     * - ADD: Increase stock (e.g., new delivery)
     * - SUBTRACT: Decrease stock (e.g., damage, loss)
     * - SET: Set to exact value (e.g., inventory correction)
     * 
     * @param sku Product SKU
     * @param request Update request with operation and quantity
     * @return Updated stock information
     */
    @PutMapping("/{sku}/stock")
    @PreAuthorize("hasRole('ADMIN') or hasRole('WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<StockInfoResponse>> updateStock(
            @PathVariable String sku,
            @Valid @RequestBody UpdateStockRequest request) {
        log.info("Updating stock for SKU: {} with operation: {}", sku, request.operation());
        StockInfoResponse response = inventoryService.updateStock(sku, request);
        return ResponseEntity.ok(
            ApiResponse.success(response, "Stock updated successfully")
        );
    }

    /**
     * Get low stock items that need reordering.
     * 
     * @return List of low stock items
     */
    @GetMapping("/alerts/low-stock")
    public ResponseEntity<ApiResponse<List<LowStockItemResponse>>> getLowStockItems() {
        log.info("Fetching low stock items");
        List<LowStockItemResponse> responses = inventoryService.getLowStockItems();
        return ResponseEntity.ok(
            ApiResponse.success(responses, "Low stock items retrieved")
        );
    }

    /**
     * Check if sufficient stock is available for multiple items.
     * Used by order-service to validate stock availability.
     * 
     * @param request Bulk stock check request
     * @return Availability check results
     */
    @PostMapping("/check-availability")
    public ResponseEntity<ApiResponse<BulkStockCheckResponse>> checkAvailability(
            @Valid @RequestBody BulkStockCheckRequest request) {
        log.info("Checking availability for {} items", request.items().size());
        BulkStockCheckResponse response = inventoryService.checkAvailability(request);
        return ResponseEntity.ok(
            ApiResponse.success(response, "Availability check completed")
        );
    }

    /**
     * Manually trigger inventory sync from product-service.
     * Useful for recovering from event processing failures.
     * 
     * @return Sync status
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> triggerSync() {
        log.info("Triggering manual inventory sync");
        // This should call a SyncService method
        inventoryService.triggerManualSync();
        return ResponseEntity.ok(
            ApiResponse.success("Sync triggered successfully", "Inventory synchronization started")
        );
    }
}
```

### Required DTO Updates

Add to `StockInfoResponse`:
```java
package com.ecommerce.inventoryservice.dto.inventory;

import java.time.LocalDateTime;
import java.util.UUID;

public record StockInfoResponse(
    UUID id,
    String sku,
    UUID variantId,
    UUID productId,
    String productName,
    String variantName,
    int availableStock,
    int reservedStock,
    int totalStock,
    boolean isLowStock,
    int reorderPoint,
    int minStockLevel,
    int maxStockLevel,
    boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

Add `CreateInventoryRequest`:
```java
package com.ecommerce.inventoryservice.dto.inventory;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateInventoryRequest(
    @NotBlank(message = "SKU is required")
    String sku,
    
    @NotNull(message = "Variant ID is required")
    UUID variantId,
    
    @NotNull(message = "Product ID is required")
    UUID productId,
    
    @NotBlank(message = "Product name is required")
    String productName,
    
    @NotBlank(message = "Variant name is required")
    String variantName,
    
    @Min(value = 0, message = "Initial stock cannot be negative")
    int initialStock,
    
    @Min(value = 0, message = "Min stock level cannot be negative")
    int minStockLevel,
    
    @Min(value = 0, message = "Reorder point cannot be negative")
    int reorderPoint
) {}
```

Add `LowStockItemResponse`:
```java
package com.ecommerce.inventoryservice.dto.inventory;

import java.util.UUID;

public record LowStockItemResponse(
    UUID inventoryId,
    String sku,
    String productName,
    String variantName,
    int availableStock,
    int reorderPoint,
    int minStockLevel
) {}
```

---

## Part 2: Reservation Service Implementation

### File: `ReservationService.java`

```java
package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.StockReservation;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import com.ecommerce.inventoryservice.domain.repository.InventoryRepository;
import com.ecommerce.inventoryservice.domain.repository.StockReservationRepository;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckRequest;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckResponse;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckResponse.ItemAvailability;
import com.ecommerce.inventoryservice.dto.reservation.*;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import com.ecommerce.inventoryservice.exception.ReservationNotFoundException;
import com.ecommerce.inventoryservice.kafka.InventoryEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing stock reservations for orders.
 * 
 * Reservation Flow:
 * 1. Order Service calls reserveStock() with order items
 * 2. Service checks availability using pessimistic locking
 * 3. If available, creates PENDING reservations and deducts from availableStock
 * 4. Order Service processes payment
 * 5. On payment success, confirmReservation() is called → CONFIRMED
 * 6. On fulfillment, releaseReservation() is called → RELEASED
 * On payment failure/order cancelled, releaseReservation() is called with reason
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final InventoryEventProducer eventProducer;

    /**
     * Check if sufficient stock is available for order items.
     * This is a read-only check - does NOT create reservations.
     * Used by order-service to validate stock before accepting order.
     * 
     * @param request Bulk stock check request
     * @return Availability check with status for each item
     */
    @Transactional(readOnly = true)
    public BulkStockCheckResponse checkAvailability(BulkStockCheckRequest request) {
        log.info("Checking availability for {} items", request.items().size());
        
        List<ItemAvailability> availabilities = request.items().stream()
            .map(item -> {
                var inventory = inventoryRepository.findBySku(item.sku());
                
                if (inventory.isEmpty()) {
                    return ItemAvailability.unavailable(item.sku(), "Inventory not found");
                }
                
                Inventory inv = inventory.get();
                if (!inv.isActive()) {
                    return ItemAvailability.unavailable(item.sku(), "Product variant is inactive");
                }
                
                if (inv.getAvailableStock() < item.quantity()) {
                    return ItemAvailability.unavailable(
                        item.sku(), 
                        String.format("Only %d units available", inv.getAvailableStock())
                    );
                }
                
                return ItemAvailability.available(
                    item.sku(),
                    inv.getAvailableStock(),
                    inv.getReservedStock()
                );
            })
            .collect(Collectors.toList());
        
        boolean allAvailable = availabilities.stream()
            .allMatch(ItemAvailability::isAvailable);
        
        log.info("Availability check complete: {} items available", 
            availabilities.stream().filter(ItemAvailability::isAvailable).count());
        
        return new BulkStockCheckResponse(allAvailable, availabilities);
    }

    /**
     * Reserve stock for a new order.
     * 
     * Process:
     * 1. Lock inventory records for all SKUs (pessimistic lock)
     * 2. Check if sufficient stock available
     * 3. Deduct from availableStock
     * 4. Add to reservedStock
     * 5. Create StockReservation records (PENDING status)
     * 6. Publish STOCK_RESERVED events
     * 
     * If any item fails, entire transaction rolls back (all-or-nothing).
     * 
     * @param request Order items to reserve
     * @return Bulk reservation response with reservation IDs
     * @throws InsufficientStockException if any item lacks stock
     */
    @Transactional
    public BulkReservationResponse reserveStock(BulkReserveStockRequest request) {
        log.info("Processing reservation for Order ID: {} with {} items", 
            request.orderId(), request.items().size());
        
        List<ReservationResponse> responses = new ArrayList<>();
        List<String> skus = request.items().stream()
            .map(BulkReserveStockRequest.ReserveItem::sku)
            .sorted()  // Important: always acquire in consistent order to prevent deadlocks
            .distinct()
            .collect(Collectors.toList());
        
        // Lock all inventory records involved
        Map<String, Inventory> inventories = new HashMap<>();
        for (String sku : skus) {
            var inventory = inventoryRepository.findBySkuForUpdate(sku);
            inventories.put(sku, inventory.orElseThrow(() -> 
                new InsufficientStockException("Inventory not found for SKU: " + sku)
            ));
        }
        
        // Check all items have sufficient stock
        for (var item : request.items()) {
            Inventory inv = inventories.get(item.sku());
            if (inv.getAvailableStock() < item.quantity()) {
                log.warn("Insufficient stock for SKU: {}. Available: {}, Requested: {}",
                    item.sku(), inv.getAvailableStock(), item.quantity());
                throw new InsufficientStockException(
                    String.format("Insufficient stock for SKU %s. Available: %d, Requested: %d",
                        item.sku(), inv.getAvailableStock(), item.quantity())
                );
            }
        }
        
        // Process reservations
        for (var item : request.items()) {
            Inventory inv = inventories.get(item.sku());
            
            // Update inventory
            inv.setAvailableStock(inv.getAvailableStock() - item.quantity());
            inv.setReservedStock(inv.getReservedStock() + item.quantity());
            inventoryRepository.save(inv);
            
            // Create reservation record
            StockReservation reservation = new StockReservation(
                inv.getId(),
                request.orderId(),
                item.quantity(),
                ReservationStatus.PENDING
            );
            StockReservation saved = reservationRepository.save(reservation);
            
            // Publish event
            eventProducer.publishStockReserved(
                saved.getId(),
                inv.getId(),
                inv.getSku(),
                inv.getVariantId(),
                inv.getProductId(),
                request.orderId(),
                item.quantity()
            );
            
            responses.add(new ReservationResponse(
                saved.getId(),
                inv.getId(),
                inv.getSku(),
                item.quantity(),
                ReservationStatus.PENDING,
                saved.getCreatedAt()
            ));
            
            log.info("Reserved {} units of SKU: {} for Order: {}",
                item.quantity(), item.sku(), request.orderId());
        }
        
        return new BulkReservationResponse(request.orderId(), responses, true);
    }

    /**
     * Confirm a pending reservation.
     * Called when order payment succeeds.
     * Changes reservation status from PENDING to CONFIRMED.
     * 
     * @param reservationId Reservation UUID
     * @param orderId Order ID (for audit)
     */
    @Transactional
    public void confirmReservation(UUID reservationId, String orderId) {
        log.info("Confirming reservation: {} for Order: {}", reservationId, orderId);
        
        StockReservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(
                "Reservation not found: " + reservationId
            ));
        
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot confirm non-PENDING reservation. Current status: " + reservation.getStatus()
            );
        }
        
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setConfirmedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        
        log.info("Reservation confirmed: {}", reservationId);
    }

    /**
     * Release a reservation and return stock to available pool.
     * Called when:
     * - Order payment fails
     * - Order is cancelled
     * - Reservation expires (timeout)
     * 
     * Changes reservation status from PENDING to RELEASED.
     * Adds quantity back to availableStock.
     * Deducts from reservedStock.
     * 
     * @param reservationId Reservation UUID
     * @param reason Reason for release (e.g., "ORDER_CANCELLED", "PAYMENT_FAILED", "EXPIRED")
     */
    @Transactional
    public void releaseReservation(UUID reservationId, String reason) {
        log.info("Releasing reservation: {} with reason: {}", reservationId, reason);
        
        StockReservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(
                "Reservation not found: " + reservationId
            ));
        
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.warn("Reservation already released: {}", reservationId);
            return;  // Idempotent
        }
        
        Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getInventoryId())
            .orElseThrow(() -> new IllegalStateException(
                "Inventory not found for reservation: " + reservationId
            ));
        
        // Return stock to available pool
        inventory.setAvailableStock(inventory.getAvailableStock() + reservation.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());
        inventoryRepository.save(inventory);
        
        // Update reservation
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        
        // Publish event
        eventProducer.publishStockReleased(
            reservation.getId(),
            inventory.getId(),
            inventory.getSku(),
            inventory.getVariantId(),
            inventory.getProductId(),
            reservation.getQuantity(),
            reason
        );
        
        log.info("Reservation released: {} with {} units returned", 
            reservationId, reservation.getQuantity());
    }

    /**
     * Get all reservations for an order.
     * 
     * @param orderId Order ID
     * @return List of reservations
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByOrderId(String orderId) {
        return reservationRepository.findByOrderId(orderId)
            .stream()
            .map(res -> new ReservationResponse(
                res.getId(),
                res.getInventoryId(),
                "SKU_FROM_INVENTORY",  // Could optimize with JOIN
                res.getQuantity(),
                res.getStatus(),
                res.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Expire old pending reservations.
     * Called by scheduled job to clean up stale reservations.
     * 
     * Reservation timeout: 24 hours
     */
    @Transactional
    public void expireOldReservations() {
        log.info("Expiring old pending reservations");
        
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(24);
        List<StockReservation> expired = reservationRepository
            .findByStatusAndCreatedAtBefore(ReservationStatus.PENDING, expiryTime);
        
        log.info("Found {} expired reservations to release", expired.size());
        
        for (StockReservation reservation : expired) {
            releaseReservation(reservation.getId(), "EXPIRED");
        }
    }
}
```

### Add Missing Method to InventoryRepository

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.id = :id")
Optional<Inventory> findByIdForUpdate(@Param("id") UUID id);
```

---

## Part 3: Kafka Event Listener

### File: `ProductEventListener.java`

```java
package com.ecommerce.inventoryservice.kafka;

import com.ecommerce.inventoryservice.dto.event.ProductEvent;
import com.ecommerce.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes product events from product-service.
 * Maintains inventory data in sync with product catalog.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {
    
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "product-events",
        groupId = "inventory-service",
        containerFactory = "productEventListenerContainerFactory"
    )
    public void handleProductEvent(String message) {
        try {
            ProductEvent event = objectMapper.readValue(message, ProductEvent.class);
            log.info("Received product event: {} for variant: {}", 
                event.getEventType(), event.getVariantId());
            
            switch (event.getEventType()) {
                case "PRODUCT_VARIANT_CREATED" -> handleVariantCreated(event);
                case "PRODUCT_VARIANT_UPDATED" -> handleVariantUpdated(event);
                case "PRODUCT_VARIANT_DELETED" -> handleVariantDeleted(event);
                default -> log.warn("Unknown product event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing product event", e);
            throw new RuntimeException("Failed to process product event", e);
        }
    }

    private void handleVariantCreated(ProductEvent event) {
        log.info("Creating inventory for variant: {}", event.getVariantId());
        
        inventoryService.initializeInventory(
            event.getSku(),
            event.getVariantId(),
            event.getProductId(),
            event.getProductName(),
            event.getVariantName(),
            0  // Initial stock is 0
        );
    }

    private void handleVariantUpdated(ProductEvent event) {
        log.info("Updating inventory metadata for variant: {}", event.getVariantId());
        
        // Update denormalized product/variant names
        inventoryService.updateInventoryMetadata(
            event.getSku(),
            event.getProductName(),
            event.getVariantName()
        );
    }

    private void handleVariantDeleted(ProductEvent event) {
        log.info("Deactivating inventory for variant: {}", event.getVariantId());
        
        // Soft delete by marking as inactive
        inventoryService.deactivateInventory(event.getVariantId());
    }
}
```

### File: `OrderEventListener.java`

```java
package com.ecommerce.inventoryservice.kafka;

import com.ecommerce.inventoryservice.dto.event.OrderEvent;
import com.ecommerce.inventoryservice.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order events from order-service.
 * Manages stock reservations for orders.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {
    
    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order-events",
        groupId = "inventory-service",
        containerFactory = "orderEventListenerContainerFactory"
    )
    public void handleOrderEvent(String message) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            log.info("Received order event: {} for order: {}", 
                event.getEventType(), event.getOrderId());
            
            switch (event.getEventType()) {
                case "ORDER_PLACED" -> handleOrderPlaced(event);
                case "ORDER_CANCELLED" -> handleOrderCancelled(event);
                case "ORDER_FULFILLED" -> handleOrderFulfilled(event);
                default -> log.warn("Unknown order event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing order event", e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }

    private void handleOrderPlaced(OrderEvent event) {
        log.info("Order placed: {}. Reserving stock...", event.getOrderId());
        // Stock reservation is handled by order-service calling REST API
        // This event can be used for logging/analytics
    }

    private void handleOrderCancelled(OrderEvent event) {
        log.info("Order cancelled: {}. Releasing reservations...", event.getOrderId());
        
        // Release all reservations for this order
        var reservations = reservationService.getReservationsByOrderId(event.getOrderId());
        for (var reservation : reservations) {
            reservationService.releaseReservation(reservation.id(), "ORDER_CANCELLED");
        }
    }

    private void handleOrderFulfilled(OrderEvent event) {
        log.info("Order fulfilled: {}. Confirming reservations...", event.getOrderId());
        
        // Confirm reservations when order is fulfilled
        var reservations = reservationService.getReservationsByOrderId(event.getOrderId());
        for (var reservation : reservations) {
            reservationService.confirmReservation(reservation.id(), event.getOrderId());
        }
    }
}
```

---

## Part 4: GlobalExceptionHandler

### File: `GlobalExceptionHandler.java`

```java
package com.ecommerce.inventoryservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import response.ApiResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for inventory-service.
 * Converts exceptions to standardized API responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInventoryNotFound(
            InventoryNotFoundException ex) {
        log.warn("Inventory not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("INVENTORY_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientStock(
            InsufficientStockException ex) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("INSUFFICIENT_STOCK", ex.getMessage()));
    }

    @ExceptionHandler(InvalidStockOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOperation(
            InvalidStockOperationException ex) {
        log.warn("Invalid stock operation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("INVALID_OPERATION", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateInventoryException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateInventory(
            DuplicateInventoryException ex) {
        log.warn("Duplicate inventory: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("DUPLICATE_INVENTORY", ex.getMessage()));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReservationNotFound(
            ReservationNotFoundException ex) {
        log.warn("Reservation not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("RESERVATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWarehouseNotFound(
            WarehouseNotFoundException ex) {
        log.warn("Warehouse not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("WAREHOUSE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WarehouseCapacityExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleCapacityExceeded(
            WarehouseCapacityExceededException ex) {
        log.warn("Warehouse capacity exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("CAPACITY_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationError(
            MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_ERROR", "Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

---

## Part 5: Configuration Files

### File: `application-dev.yml`

```yaml
spring:
  application:
    name: inventory-service
  
  datasource:
    url: jdbc:mysql://localhost:3306/inventory_db?useSSL=false&serverTimezone=UTC
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
      connection-timeout: 20000
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
        show_sql: false
        use_sql_comments: true
    show-sql: false
  
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: inventory-service
      auto-offset-reset: earliest
      max-poll-records: 100
    producer:
      acks: all
      retries: 3
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

server:
  port: 8083
  servlet:
    context-path: /

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      simple:
        enabled: true

logging:
  level:
    root: INFO
    com.ecommerce: DEBUG
    org.springframework.kafka: DEBUG
    org.hibernate.SQL: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

kafka:
  topics:
    inventory-events: inventory-events
    product-events: product-events
    order-events: order-events
  deadletter:
    suffix: -dlq
```

### File: `application-prod.yml`

```yaml
spring:
  application:
    name: inventory-service
  
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: false
        show_sql: false
        jdbc:
          batch_size: 20
          fetch_size: 50
    show-sql: false
  
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: inventory-service
      auto-offset-reset: earliest
      max-poll-records: 500
      session-timeout-ms: 30000
    producer:
      acks: all
      retries: 5
      linger-ms: 10
      batch-size: 32768
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

server:
  port: 8083
  servlet:
    context-path: /

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    root: WARN
    com.ecommerce: INFO
    org.springframework.kafka: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

kafka:
  topics:
    inventory-events: inventory-events
    product-events: product-events
    order-events: order-events
  deadletter:
    suffix: -dlq
```

---

## Part 6: Required DTO Classes

### Add to existing DTOs:

**BulkStockCheckResponse.java**:
```java
package com.ecommerce.inventoryservice.dto.inventory;

import java.util.List;

public record BulkStockCheckResponse(
    boolean available,
    List<ItemAvailability> items
) {
    public record ItemAvailability(
        String sku,
        boolean available,
        String reason,
        int currentStock,
        int reservedStock
    ) {
        public static ItemAvailability available(String sku, int current, int reserved) {
            return new ItemAvailability(sku, true, null, current, reserved);
        }
        
        public static ItemAvailability unavailable(String sku, String reason) {
            return new ItemAvailability(sku, false, reason, 0, 0);
        }
    }
}
```

**ReservationResponse.java**:
```java
package com.ecommerce.inventoryservice.dto.reservation;

import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    UUID inventoryId,
    String sku,
    int quantity,
    ReservationStatus status,
    LocalDateTime createdAt
) {}
```

---

## Estimated Implementation Time

| Component | Estimated Time | Complexity |
|-----------|----------------|-----------|
| InventoryController | 3 hours | Medium |
| ReservationService | 4 hours | High |
| ReservationController | 2 hours | Low |
| ProductEventListener | 2 hours | Medium |
| OrderEventListener | 1 hour | Low |
| GlobalExceptionHandler | 1 hour | Low |
| Configuration Files | 1 hour | Low |
| Testing | 8+ hours | Medium |
| **Total** | **22 hours** | **3-4 days** |

---

## Testing Approach

After implementation, test in this order:

1. **Unit Tests** (2 hours):
   - Repository methods
   - Service logic
   - Exception handling

2. **Integration Tests** (3 hours):
   - REST endpoints
   - Database transactions
   - Kafka event processing

3. **End-to-End Tests** (2 hours):
   - Full order flow
   - Reservation lifecycle
   - Concurrency scenarios

4. **Performance Tests** (1 hour):
   - Concurrent requests
   - Large batch operations
   - Database connection pool

---

**Ready to Start?** Begin with Part 1 (InventoryController) as it's the foundation for testing other components.


