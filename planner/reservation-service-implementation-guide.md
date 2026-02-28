# Reservation Service - Implementation Guide

**For:** Developers implementing ReservationService and ReservationController  
**Status:** Ready for Implementation  
**Focus:** Controller-Service Separation & Business Logic Delegation

---

## Overview

Your **ReservationController is already correctly implemented!** It follows the best practice of keeping controllers thin and delegating all business logic to the service layer.

This document explains:
1. Why the current pattern is correct
2. What the ReservationService should handle
3. Complete ReservationService implementation
4. DTOs and supporting structures
5. How to extend the controller with new features

---

## Current State Analysis

### ✅ ReservationController - GOOD PATTERN

Your controller currently:

```java
@PostMapping("/reserve")
public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
        @Valid @RequestBody ReserveStockRequest request) {
    log.info("POST /api/reservations/reserve - Order: {}, SKU: {}, Quantity: {}",
            request.orderId(), request.sku(), request.quantity());

    // ✅ Delegates to service - NO business logic here!
    ReservationResponse response = reservationService.reserveSingleStock(request);
    
    // ✅ Only handles HTTP response formatting
    return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success("201", "Stock reserved successfully", response)
    );
}
```

### Why This Is Correct

| Concern | Controller | Service |
|---------|------------|---------|
| **HTTP Parsing** | ✅ | ❌ |
| **HTTP Response** | ✅ | ❌ |
| **Input Validation** | ✅ (@Valid) | ✅ (double-check) |
| **Business Logic** | ❌ | ✅ |
| **Database Access** | ❌ | ✅ |
| **Transactions** | ❌ | ✅ (@Transactional) |
| **Error Handling** | ⚠️ (Global Handler) | ✅ |

---

## What Should NOT Be in Controller

❌ **DON'T DO THIS:**

```java
// ❌ BAD - Business logic in controller
@PostMapping("/reserve")
public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
        @Valid @RequestBody ReserveStockRequest request) {
    
    // ❌ Finding inventory in controller
    Inventory inventory = inventoryRepo.findBySku(request.sku());
    if (inventory == null) throw new NotFoundException();
    
    // ❌ Checking stock in controller
    if (inventory.getAvailableStock() < request.quantity()) {
        throw new InsufficientStockException();
    }
    
    // ❌ Creating entities in controller
    StockReservation reservation = new StockReservation();
    reservation.setInventoryId(inventory.getId());
    // ... more setup
    
    // ❌ Saving in controller
    StockReservation saved = reservationRepo.save(reservation);
    
    // ❌ Publishing events in controller
    kafkaProducer.publishEvent(...);
    
    // ❌ Converting to DTO in controller
    ReservationResponse response = new ReservationResponse(...);
    
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

✅ **DO THIS INSTEAD:**

```java
// ✅ GOOD - Thin controller
@PostMapping("/reserve")
public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
        @Valid @RequestBody ReserveStockRequest request) {
    log.info("POST /api/reservations/reserve");
    
    // ONE LINE: Delegate to service
    ReservationResponse response = reservationService.reserveSingleStock(request);
    
    // Return formatted response
    return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success("201", "Stock reserved successfully", response)
    );
}
```

---

## ReservationService - Complete Implementation

### Service Structure

```
ReservationService
├── Single Reservations
│   ├── reserveSingleStock()
│   └── reserve internal logic with locks
├── Bulk Reservations  
│   ├── reserveBulkStock()
│   └── atomic multi-item reservation
├── Availability Checks
│   └── checkAvailability()
├── Reservation Lifecycle
│   ├── confirmReservation()
│   ├── releaseReservation()
│   ├── fulfillReservation()
│   └── expireReservation()
├── Queries
│   ├── getReservationById()
│   ├── getOrderReservationResponses()
│   ├── getActiveReservations()
│   └── getExpiredReservations()
└── Cleanup
    └── cleanupExpiredReservations()
```

### Complete ReservationService Implementation

```java
package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.StockReservation;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import com.ecommerce.inventoryservice.domain.repository.InventoryRepository;
import com.ecommerce.inventoryservice.domain.repository.StockReservationRepository;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckRequest;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckResponse;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckResponse.AvailabilityResult;
import com.ecommerce.inventoryservice.dto.reservation.*;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
import com.ecommerce.inventoryservice.exception.ReservationNotFoundException;
import com.ecommerce.inventoryservice.kafka.InventoryEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing stock reservations for orders.
 *
 * Key Responsibilities:
 * 1. Reserve stock for orders (single and bulk)
 * 2. Confirm/release/fulfill reservations
 * 3. Clean up expired reservations
 * 4. Provide availability checks without reserving
 * 5. Publish events for other services
 *
 * Concurrency Strategy:
 * - Pessimistic locking when reserving stock (prevent overselling)
 * - Optimistic locking on version field (detect conflicts)
 * - Atomic SQL operations (minimize lock time)
 * - Retry logic for version conflicts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository stockReservationRepository;
    private final InventoryEventProducer eventProducer;
    
    @Value("${inventory.reservation.expiry-minutes:15}")
    private long reservationExpiryMinutes;
    
    @Value("${inventory.reservation.max-retries:3}")
    private int maxRetries;
    
    // ==================== Single Stock Reservation ====================
    
    /**
     * Reserve stock for a single item.
     * Uses pessimistic locking to prevent race conditions.
     *
     * Flow:
     * 1. Lock inventory record
     * 2. Check available stock
     * 3. Create reservation record
     * 4. Update inventory atomically
     * 5. Publish event
     * 6. Release lock
     *
     * @param request Reserve stock request (SKU, orderId, quantity)
     * @return Reservation response with reservation ID and status
     * @throws InventoryNotFoundException if inventory not found for SKU
     * @throws InsufficientStockException if stock not available
     */
    @Transactional
    public ReservationResponse reserveSingleStock(ReserveStockRequest request) {
        log.info("Reserving stock - Order: {}, SKU: {}, Quantity: {}",
                request.orderId(), request.sku(), request.quantity());
        
        try {
            // Step 1: Lock and fetch inventory
            Inventory inventory = inventoryRepository.findBySkuForUpdate(request.sku())
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "Inventory not found for SKU: " + request.sku()
                    ));
            
            // Step 2: Validate stock availability
            if (inventory.getAvailableStock() < request.quantity()) {
                log.warn("Insufficient stock - SKU: {}, Available: {}, Requested: {}",
                        request.sku(), inventory.getAvailableStock(), request.quantity());
                throw new InsufficientStockException(
                        "Insufficient stock for SKU: " + request.sku(),
                        request.sku(),
                        request.quantity(),
                        inventory.getAvailableStock()
                );
            }
            
            // Step 3: Create reservation record
            StockReservation reservation = new StockReservation(
                    inventory.getId(),
                    request.orderId(),
                    request.quantity(),
                    Instant.now().plusSeconds(reservationExpiryMinutes * 60)
            );
            
            StockReservation savedReservation = stockReservationRepository.save(reservation);
            
            // Step 4: Update inventory atomically
            int updated = inventoryRepository.atomicReserveStock(
                    inventory.getId(),
                    request.quantity()
            );
            
            if (updated == 0) {
                // Stock was reserved by another transaction
                log.warn("Stock was reserved concurrently - SKU: {}", request.sku());
                throw new InsufficientStockException(
                        "Stock was reserved by another request",
                        request.sku(),
                        request.quantity(),
                        0
                );
            }
            
            log.info("Stock reserved successfully - Reservation ID: {}, SKU: {}, Quantity: {}",
                    savedReservation.getId(), request.sku(), request.quantity());
            
            // Step 5: Publish event (outside transaction if possible)
            eventProducer.publishStockReserved(
                    inventory.getId(),
                    inventory.getSku(),
                    inventory.getVariantId(),
                    inventory.getProductId(),
                    request.quantity(),
                    request.orderId()
            );
            
            // Step 6: Return response
            return toReservationResponse(savedReservation, inventory);
            
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict during reservation - SKU: {}, retrying...", request.sku());
            throw new RuntimeException("Concurrent modification detected, please retry", e);
        }
    }
    
    // ==================== Bulk Stock Reservation ====================
    
    /**
     * Reserve stock for multiple items in atomic operation.
     * All items are reserved or none are reserved (all-or-nothing).
     *
     * Flow:
     * 1. Validate all items have available stock
     * 2. Create all reservation records
     * 3. Update inventory for each item atomically
     * 4. Publish events
     * 5. Return consolidated response
     *
     * @param request Bulk reserve request (orderId + list of items)
     * @return Bulk reservation response with all reservation details
     * @throws InsufficientStockException if any item not available
     */
    @Transactional
    public BulkReservationResponse reserveBulkStock(BulkReserveStockRequest request) {
        log.info("Bulk reserving stock - Order: {}, Items: {}",
                request.orderId(), request.items().size());
        
        List<ReservationResponse> reservations = new ArrayList<>();
        List<String> events = new ArrayList<>();
        
        try {
            // Phase 1: Validate all items
            Map<String, Inventory> inventoryMap = new HashMap<>();
            for (BulkReserveStockRequest.ReservationItem item : request.items()) {
                Inventory inventory = inventoryRepository.findBySkuForUpdate(item.sku())
                        .orElseThrow(() -> new InventoryNotFoundException(
                                "Inventory not found for SKU: " + item.sku()
                        ));
                
                if (inventory.getAvailableStock() < item.quantity()) {
                    throw new InsufficientStockException(
                            "Insufficient stock for SKU: " + item.sku(),
                            item.sku(),
                            item.quantity(),
                            inventory.getAvailableStock()
                    );
                }
                
                inventoryMap.put(item.sku(), inventory);
            }
            
            // Phase 2: Reserve all items
            Instant expiryTime = Instant.now().plusSeconds(reservationExpiryMinutes * 60);
            
            for (BulkReserveStockRequest.ReservationItem item : request.items()) {
                Inventory inventory = inventoryMap.get(item.sku());
                
                // Create reservation
                StockReservation reservation = new StockReservation(
                        inventory.getId(),
                        request.orderId(),
                        item.quantity(),
                        expiryTime
                );
                
                StockReservation savedReservation = stockReservationRepository.save(reservation);
                
                // Update inventory atomically
                int updated = inventoryRepository.atomicReserveStock(
                        inventory.getId(),
                        item.quantity()
                );
                
                if (updated == 0) {
                    throw new InsufficientStockException(
                            "Stock was reserved concurrently for SKU: " + item.sku(),
                            item.sku(),
                            item.quantity(),
                            0
                    );
                }
                
                reservations.add(toReservationResponse(savedReservation, inventory));
                
                // Create event for publishing
                events.add(createStockReservedEvent(
                        inventory.getId(),
                        inventory.getSku(),
                        inventory.getVariantId(),
                        inventory.getProductId(),
                        item.quantity(),
                        request.orderId()
                ));
                
                log.info("Item reserved in bulk - SKU: {}, Quantity: {}", item.sku(), item.quantity());
            }
            
            // Phase 3: Publish events
            events.forEach(eventProducer::publishEvent);
            
            log.info("Bulk reservation successful - Order: {}, Items: {}", 
                    request.orderId(), reservations.size());
            
            return new BulkReservationResponse(
                    request.orderId(),
                    reservations,
                    true,
                    expiryTime
            );
            
        } catch (Exception e) {
            log.error("Bulk reservation failed - Order: {}", request.orderId(), e);
            throw e;
        }
    }
    
    // ==================== Availability Checks ====================
    
    /**
     * Check stock availability without reserving.
     * Used by clients to verify stock before initiating checkout.
     *
     * @param request Items to check
     * @return Availability results for each item
     */
    @Transactional(readOnly = true)
    public BulkStockCheckResponse checkAvailability(BulkStockCheckRequest request) {
        log.info("Checking availability for {} items", request.items().size());
        
        List<AvailabilityResult> results = request.items().stream()
                .map(item -> {
                    Optional<Inventory> optionalInventory = inventoryRepository.findBySku(item.sku());
                    
                    if (optionalInventory.isEmpty()) {
                        return new AvailabilityResult(
                                item.sku(),
                                item.requestedQuantity(),
                                0,
                                false,
                                item.requestedQuantity()
                        );
                    }
                    
                    Inventory inventory = optionalInventory.get();
                    boolean isAvailable = inventory.getAvailableStock() >= item.requestedQuantity();
                    int shortfall = Math.max(0, item.requestedQuantity() - inventory.getAvailableStock());
                    
                    return new AvailabilityResult(
                            item.sku(),
                            item.requestedQuantity(),
                            inventory.getAvailableStock(),
                            isAvailable,
                            shortfall
                    );
                })
                .collect(Collectors.toList());
        
        boolean allAvailable = results.stream().allMatch(AvailabilityResult::isAvailable);
        
        log.info("Availability check complete - All Available: {}", allAvailable);
        
        return new BulkStockCheckResponse(results, allAvailable);
    }
    
    // ==================== Reservation Lifecycle ====================
    
    /**
     * Confirm a pending reservation (called after successful payment).
     * Changes status from ACTIVE to CONFIRMED.
     *
     * @param reservationId ID of reservation to confirm
     * @return Confirmed reservation details
     * @throws ReservationNotFoundException if not found
     */
    @Transactional
    public ReservationResponse confirmReservation(UUID reservationId) {
        log.info("Confirming reservation: {}", reservationId);
        
        try {
            StockReservation reservation = stockReservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(
                            "Reservation not found with ID: " + reservationId
                    ));
            
            if (reservation.getStatus() != ReservationStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Cannot confirm reservation in status: " + reservation.getStatus()
                );
            }
            
            // Atomic confirm
            int updated = stockReservationRepository.atomicConfirmReservation(
                    reservationId,
                    ReservationStatus.ACTIVE,
                    ReservationStatus.CONFIRMED
            );
            
            if (updated == 0) {
                throw new RuntimeException("Failed to confirm reservation - status may have changed");
            }
            
            // Fetch updated reservation
            StockReservation confirmed = stockReservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(
                            "Reservation not found after confirmation: " + reservationId
                    ));
            
            Inventory inventory = inventoryRepository.findById(confirmed.getInventoryId())
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "Inventory not found for reservation: " + reservationId
                    ));
            
            log.info("Reservation confirmed: {}, SKU: {}", reservationId, inventory.getSku());
            
            // Publish event
            eventProducer.publishStockReservationConfirmed(
                    inventory.getId(),
                    inventory.getSku(),
                    inventory.getVariantId(),
                    confirmed.getOrderId(),
                    confirmed.getQuantity()
            );
            
            return toReservationResponse(confirmed, inventory);
            
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict during confirmation - ID: {}, retrying...", reservationId);
            throw new RuntimeException("Concurrent modification detected, please retry", e);
        }
    }
    
    /**
     * Release a reservation (called on payment failure or cancellation).
     * Changes status to RELEASED and restores available stock.
     *
     * @param reservationId ID of reservation to release
     * @param reason Reason for release (e.g., PAYMENT_FAILED, ORDER_CANCELLED)
     * @return Released reservation details
     * @throws ReservationNotFoundException if not found
     */
    @Transactional
    public ReservationResponse releaseReservation(UUID reservationId, String reason) {
        log.info("Releasing reservation: {} due to: {}", reservationId, reason);
        
        try {
            StockReservation reservation = stockReservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(
                            "Reservation not found with ID: " + reservationId
                    ));
            
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                log.warn("Reservation already released: {}", reservationId);
                Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                        .orElseThrow();
                return toReservationResponse(reservation, inventory);
            }
            
            Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getInventoryId())
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "Inventory not found for reservation: " + reservationId
                    ));
            
            // Atomic release - restore stock
            int updated = inventoryRepository.atomicReleaseReservation(
                    reservation.getInventoryId(),
                    reservation.getQuantity()
            );
            
            if (updated == 0) {
                throw new RuntimeException("Failed to release reservation - inventory may have changed");
            }
            
            // Update reservation status
            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setCancelledAt(Instant.now());
            StockReservation released = stockReservationRepository.save(reservation);
            
            log.info("Reservation released: {}, SKU: {}, Reason: {}",
                    reservationId, inventory.getSku(), reason);
            
            // Publish event
            eventProducer.publishStockReserved(
                    inventory.getId(),
                    inventory.getSku(),
                    inventory.getVariantId(),
                    inventory.getProductId(),
                    -reservation.getQuantity(),  // Negative quantity indicates release
                    reservation.getOrderId()
            );
            
            return toReservationResponse(released, inventory);
            
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict during release - ID: {}, retrying...", reservationId);
            throw new RuntimeException("Concurrent modification detected, please retry", e);
        }
    }
    
    /**
     * Fulfill a reservation (called after order fulfillment).
     * Changes status to FULFILLED.
     *
     * @param reservationId ID of reservation to fulfill
     * @return Fulfilled reservation details
     */
    @Transactional
    public ReservationResponse fulfillReservation(UUID reservationId) {
        log.info("Fulfilling reservation: {}", reservationId);
        
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found with ID: " + reservationId
                ));
        
        if (reservation.getStatus() == ReservationStatus.FULFILLED) {
            log.warn("Reservation already fulfilled: {}", reservationId);
            Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                    .orElseThrow();
            return toReservationResponse(reservation, inventory);
        }
        
        // Update status
        reservation.setStatus(ReservationStatus.FULFILLED);
        reservation.setFulfilledAt(Instant.now());
        StockReservation fulfilled = stockReservationRepository.save(reservation);
        
        Inventory inventory = inventoryRepository.findById(fulfilled.getInventoryId())
                .orElseThrow();
        
        log.info("Reservation fulfilled: {}, SKU: {}", reservationId, inventory.getSku());
        
        return toReservationResponse(fulfilled, inventory);
    }
    
    // ==================== Query Methods ====================
    
    /**
     * Get a specific reservation by ID.
     *
     * @param reservationId Reservation ID
     * @return Reservation response
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(UUID reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found with ID: " + reservationId
                ));
        
        Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                .orElseThrow();
        
        return toReservationResponse(reservation, inventory);
    }
    
    /**
     * Get all reservations for a specific order.
     *
     * @param orderId Order ID
     * @return List of reservation responses
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getOrderReservationResponses(String orderId) {
        log.info("Fetching reservations for order: {}", orderId);
        
        return stockReservationRepository.findByOrderId(orderId).stream()
                .map(reservation -> {
                    Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                            .orElseThrow();
                    return toReservationResponse(reservation, inventory);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get all active reservations.
     *
     * @return List of active reservation responses
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getActiveReservations() {
        return stockReservationRepository.findByStatus(ReservationStatus.ACTIVE).stream()
                .map(reservation -> {
                    Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                            .orElseThrow();
                    return toReservationResponse(reservation, inventory);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get all expired reservations.
     *
     * @return List of expired reservation responses
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getExpiredReservations() {
        Instant now = Instant.now();
        return stockReservationRepository.findExpiredReservations(now).stream()
                .map(reservation -> {
                    Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                            .orElseThrow();
                    return toReservationResponse(reservation, inventory);
                })
                .collect(Collectors.toList());
    }
    
    // ==================== Cleanup & Maintenance ====================
    
    /**
     * Clean up expired reservations.
     * Called periodically by scheduled task.
     * Releases expired reservations and restores stock.
     *
     * @return Number of reservations cleaned up
     */
    @Transactional
    public int cleanupExpiredReservations() {
        log.info("Starting cleanup of expired reservations");
        
        Instant now = Instant.now();
        List<StockReservation> expired = stockReservationRepository.findExpiredReservations(now);
        
        int count = 0;
        for (StockReservation reservation : expired) {
            try {
                releaseReservationInternal(reservation.getId(), "EXPIRED");
                count++;
            } catch (Exception e) {
                log.error("Failed to clean up expired reservation: {}", reservation.getId(), e);
            }
        }
        
        log.info("Expired reservations cleanup complete - Count: {}", count);
        return count;
    }
    
    /**
     * Internal method for releasing reservations (used by cleanup).
     * Does not publish external events.
     */
    @Transactional
    public void releaseReservationInternal(UUID reservationId, String reason) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found with ID: " + reservationId
                ));
        
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return;
        }
        
        Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getInventoryId())
                .orElseThrow();
        
        // Restore stock
        inventoryRepository.atomicReleaseReservation(
                reservation.getInventoryId(),
                reservation.getQuantity()
        );
        
        // Update reservation
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setCancelledAt(Instant.now());
        stockReservationRepository.save(reservation);
        
        log.info("Released reservation: {} due to: {}", reservationId, reason);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Convert StockReservation entity to response DTO.
     */
    private ReservationResponse toReservationResponse(StockReservation reservation, Inventory inventory) {
        return new ReservationResponse(
                reservation.getId(),
                inventory.getSku(),
                reservation.getOrderId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt()
        );
    }
    
    /**
     * Create stock reserved event string for publishing.
     */
    private String createStockReservedEvent(UUID inventoryId, String sku, UUID variantId,
                                           UUID productId, int quantity, String orderId) {
        // Create and serialize event
        // Implementation depends on event format
        return String.format("STOCK_RESERVED|%s|%s|%d|%s", 
                inventoryId, sku, quantity, orderId);
    }
}
```

---

## Required Repository Methods

### StockReservationRepository

```java
package com.ecommerce.inventoryservice.domain.repository;

import com.ecommerce.inventoryservice.domain.entity.StockReservation;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /**
     * Find all reservations for an order.
     */
    List<StockReservation> findByOrderId(String orderId);

    /**
     * Find all reservations with a specific status.
     */
    List<StockReservation> findByStatus(ReservationStatus status);

    /**
     * Find all expired reservations.
     */
    @Query("SELECT r FROM StockReservation r WHERE r.expiresAt < :now AND r.status = 'ACTIVE'")
    List<StockReservation> findExpiredReservations(@Param("now") Instant now);

    /**
     * Find reservations for an order with specific status.
     */
    List<StockReservation> findByOrderIdAndStatus(String orderId, ReservationStatus status);

    /**
     * Atomic confirm reservation (update status with version check).
     */
    @Modifying
    @Transactional
    @Query("UPDATE StockReservation r SET r.status = :newStatus, " +
           "r.fulfilledAt = CURRENT_TIMESTAMP WHERE r.id = :id AND r.status = :currentStatus")
    int atomicConfirmReservation(@Param("id") UUID id,
                                 @Param("currentStatus") ReservationStatus currentStatus,
                                 @Param("newStatus") ReservationStatus newStatus);

    /**
     * Atomic release reservation.
     */
    @Modifying
    @Transactional
    @Query("UPDATE StockReservation r SET r.status = :newStatus, " +
           "r.cancelledAt = CURRENT_TIMESTAMP WHERE r.id = :id AND r.status = :currentStatus")
    int atomicReleaseReservation(@Param("id") UUID id,
                                @Param("currentStatus") ReservationStatus currentStatus,
                                @Param("newStatus") ReservationStatus newStatus);
}
```

### InventoryRepository - Additional Methods

```java
/**
 * Atomic reserve stock - deduct from available, add to reserved.
 * Returns 1 if successful, 0 if stock insufficient.
 */
@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :quantity, " +
       "i.reservedStock = i.reservedStock + :quantity " +
       "WHERE i.id = :inventoryId AND i.availableStock >= :quantity")
int atomicReserveStock(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

/**
 * Atomic release reserved stock - add to available, subtract from reserved.
 */
@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :quantity, " +
       "i.reservedStock = i.reservedStock - :quantity " +
       "WHERE i.id = :inventoryId")
int atomicReleaseReservation(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

/**
 * Find by ID with pessimistic lock for updates.
 */
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.id = :id")
Optional<Inventory> findByIdForUpdate(@Param("id") UUID id);
```

---

## DTOs

### ReservationResponse

```java
package com.ecommerce.inventoryservice.dto.reservation;

import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
    UUID reservationId,
    String sku,
    String orderId,
    int quantity,
    ReservationStatus status,
    Instant expiresAt,
    Instant createdAt
) {}
```

### ReserveStockRequest

```java
package com.ecommerce.inventoryservice.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReserveStockRequest(
    @NotBlank(message = "SKU is required") String sku,
    @NotBlank(message = "Order ID is required") String orderId,
    @Min(value = 1, message = "Quantity must be at least 1") int quantity
) {}
```

### BulkReserveStockRequest

```java
package com.ecommerce.inventoryservice.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkReserveStockRequest(
    @NotBlank(message = "Order ID is required") String orderId,
    @NotEmpty(message = "Items list cannot be empty") List<@Valid ReservationItem> items
) {
    public record ReservationItem(
        @NotBlank(message = "SKU is required") String sku,
        @Min(value = 1, message = "Quantity must be at least 1") int quantity
    ) {}
}
```

### BulkReservationResponse

```java
package com.ecommerce.inventoryservice.dto.reservation;

import java.time.Instant;
import java.util.List;

public record BulkReservationResponse(
    String orderId,
    List<ReservationResponse> reservations,
    boolean allSuccessful,
    Instant expiresAt
) {}
```

### UpdateReservationRequest

```java
package com.ecommerce.inventoryservice.dto.reservation;

import jakarta.validation.constraints.NotBlank;

public record UpdateReservationRequest(
    @NotBlank(message = "Action is required") String action,  // confirm, release, fulfill
    String reason  // Optional reason for action
) {}
```

---

## ReservationController - Complete Implementation

```java
package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckRequest;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckResponse;
import com.ecommerce.inventoryservice.dto.reservation.*;
import com.ecommerce.inventoryservice.service.ReservationService;
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
 * REST Controller for stock reservation operations.
 *
 * KEY PRINCIPLE: This controller is THIN and STATELESS.
 * - Only handles HTTP concerns (request parsing, response formatting)
 * - All business logic is in ReservationService
 * - All data operations go through repositories
 *
 * Response Format: All responses use ApiResponse wrapper for consistency
 * Error Handling: Exceptions are caught by GlobalExceptionHandler
 * Authentication: @PreAuthorize ensures proper access control
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Slf4j
public class ReservationController {
    
    private final ReservationService reservationService;

    /**
     * Reserve stock for a single item.
     * POST /api/reservations/reserve
     *
     * Request Body:
     * {
     *   "sku": "SKU-001",
     *   "orderId": "ORDER-123",
     *   "quantity": 5
     * }
     *
     * Response: 201 Created
     * {
     *   "code": "201",
     *   "message": "Stock reserved successfully",
     *   "data": { ... reservation details ... }
     * }
     */
    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
            @Valid @RequestBody ReserveStockRequest request) {
        
        log.info("POST /api/reservations/reserve - Order: {}, SKU: {}, Quantity: {}",
                request.orderId(), request.sku(), request.quantity());
        
        // ✅ SINGLE LINE: All business logic delegated to service
        ReservationResponse response = reservationService.reserveSingleStock(request);
        
        // ✅ Format response
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("201", "Stock reserved successfully", response)
        );
    }

    /**
     * Reserve stock for multiple items (atomic operation).
     * POST /api/reservations/bulk-reserve
     *
     * All items reserved or none reserved.
     *
     * Request Body:
     * {
     *   "orderId": "ORDER-123",
     *   "items": [
     *     { "sku": "SKU-001", "quantity": 5 },
     *     { "sku": "SKU-002", "quantity": 3 }
     *   ]
     * }
     *
     * Response: 201 Created
     * {
     *   "orderId": "ORDER-123",
     *   "reservations": [ ... ],
     *   "allSuccessful": true,
     *   "expiresAt": "2026-02-27T14:45:00Z"
     * }
     */
    @PostMapping("/bulk-reserve")
    public ResponseEntity<ApiResponse<BulkReservationResponse>> bulkReserveStock(
            @Valid @RequestBody BulkReserveStockRequest request) {
        
        log.info("POST /api/reservations/bulk-reserve - Order: {}, Items: {}",
                request.orderId(), request.items().size());
        
        BulkReservationResponse response = reservationService.reserveBulkStock(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("201", "Bulk reservation processed", response)
        );
    }

    /**
     * Check stock availability without reserving.
     * POST /api/reservations/check-availability
     *
     * Request Body:
     * {
     *   "items": [
     *     { "sku": "SKU-001", "requestedQuantity": 5 },
     *     { "sku": "SKU-002", "requestedQuantity": 3 }
     *   ]
     * }
     *
     * Response: 200 OK
     * {
     *   "results": [
     *     { "sku": "SKU-001", "available": true, ... },
     *     { "sku": "SKU-002", "available": false, "shortfall": 2 }
     *   ],
     *   "allAvailable": false
     * }
     */
    @PostMapping("/check-availability")
    public ResponseEntity<ApiResponse<BulkStockCheckResponse>> checkAvailability(
            @Valid @RequestBody BulkStockCheckRequest request) {
        
        log.info("POST /api/reservations/check-availability - Items: {}", request.items().size());
        
        BulkStockCheckResponse response = reservationService.checkAvailability(request);
        
        return ResponseEntity.ok(
                ApiResponse.success("200", "Availability check completed", response)
        );
    }

    /**
     * Confirm a pending reservation (called after payment success).
     * POST /api/reservations/{reservationId}/confirm
     *
     * Response: 200 OK - Updated reservation details
     */
    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ApiResponse<ReservationResponse>> confirmReservation(
            @PathVariable UUID reservationId) {
        
        log.info("POST /api/reservations/{}/confirm", reservationId);
        
        ReservationResponse response = reservationService.confirmReservation(reservationId);
        
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservation confirmed successfully", response)
        );
    }

    /**
     * Release a reservation (payment failure or cancellation).
     * POST /api/reservations/{reservationId}/release
     *
     * Request Body:
     * {
     *   "action": "release",
     *   "reason": "Payment failed"
     * }
     *
     * Response: 200 OK - Released reservation details
     */
    @PostMapping("/{reservationId}/release")
    public ResponseEntity<ApiResponse<ReservationResponse>> releaseReservation(
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationRequest request) {
        
        log.info("POST /api/reservations/{}/release - Reason: {}", reservationId, request.reason());
        
        ReservationResponse response = reservationService.releaseReservation(
                reservationId, 
                request.reason()
        );
        
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservation released successfully", response)
        );
    }

    /**
     * Get all reservations for a specific order.
     * GET /api/reservations/order/{orderId}
     *
     * Response: 200 OK - List of reservations
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getOrderReservations(
            @PathVariable String orderId) {
        
        log.info("GET /api/reservations/order/{}", orderId);
        
        List<ReservationResponse> responses = reservationService.getOrderReservationResponses(orderId);
        
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservations retrieved successfully", responses)
        );
    }

    /**
     * Get details of a specific reservation by ID.
     * GET /api/reservations/{reservationId}
     *
     * Response: 200 OK - Reservation details
     */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationDetails(
            @PathVariable UUID reservationId) {
        
        log.info("GET /api/reservations/{}", reservationId);
        
        ReservationResponse response = reservationService.getReservationById(reservationId);
        
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservation details retrieved successfully", response)
        );
    }

    /**
     * Get all active reservations (Admin endpoint).
     * GET /api/reservations/admin/active
     *
     * Requires ADMIN role
     * Response: 200 OK - List of all active reservations
     */
    @GetMapping("/admin/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getActiveReservations() {
        
        log.info("GET /api/reservations/admin/active");
        
        List<ReservationResponse> responses = reservationService.getActiveReservations();
        
        return ResponseEntity.ok(
                ApiResponse.success("200", "Active reservations retrieved successfully", responses)
        );
    }

    /**
     * Get all expired reservations (Admin endpoint).
     * GET /api/reservations/admin/expired
     *
     * Requires ADMIN role
     * Response: 200 OK - List of expired reservations
     */
    @GetMapping("/admin/expired")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getExpiredReservations() {
        
        log.info("GET /api/reservations/admin/expired");
        
        List<ReservationResponse> responses = reservationService.getExpiredReservations();
        
        return ResponseEntity.ok(
                ApiResponse.success("200", "Expired reservations retrieved successfully", responses)
        );
    }

    /**
     * Clean up expired reservations (Admin endpoint).
     * POST /api/reservations/admin/cleanup-expired
     *
     * Requires ADMIN role
     * Response: 200 OK - Cleanup confirmation
     */
    @PostMapping("/admin/cleanup-expired")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> cleanupExpiredReservations() {
        
        log.info("POST /api/reservations/admin/cleanup-expired");
        
        int count = reservationService.cleanupExpiredReservations();
        
        return ResponseEntity.ok(
                ApiResponse.success(
                        "200", 
                        "Expired reservations cleaned up successfully",
                        "Cleaned up " + count + " expired reservations"
                )
        );
    }
}
```

---

## Scheduled Task for Cleanup

Create a scheduled task to clean up expired reservations:

```java
package com.ecommerce.inventoryservice.config;

import com.ecommerce.inventoryservice.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationCleanupScheduler {
    
    private final ReservationService reservationService;
    
    /**
     * Run cleanup every 10 minutes.
     * Scans for expired reservations and releases them.
     */
    @Scheduled(fixedDelay = 600000)  // 10 minutes
    public void cleanupExpiredReservations() {
        try {
            log.info("Starting scheduled cleanup of expired reservations");
            int count = reservationService.cleanupExpiredReservations();
            log.info("Scheduled cleanup completed - {} reservations cleaned up", count);
        } catch (Exception e) {
            log.error("Error during scheduled cleanup of expired reservations", e);
        }
    }
}
```

---

## Summary

Your **ReservationController is already correctly implemented** ✅

- Controllers are thin and stateless
- All business logic is in the service
- Service handles transactions and concurrency
- Controller only handles HTTP formatting

This is the **CORRECT architectural pattern** for microservices!

The remaining work is to:
1. Implement the complete ReservationService (provided above)
2. Add missing repository methods
3. Add @Version annotation to entities
4. Create database migration
5. Implement optimistic locking retry logic
6. Add comprehensive tests

