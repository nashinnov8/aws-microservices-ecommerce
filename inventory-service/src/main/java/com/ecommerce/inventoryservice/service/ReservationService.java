package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.StockReservation;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import com.ecommerce.inventoryservice.domain.repository.InventoryRepository;
import com.ecommerce.inventoryservice.domain.repository.StockReservationRepository;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckRequest;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckResponse;
import com.ecommerce.inventoryservice.dto.reservation.ReservationResponse;
import com.ecommerce.inventoryservice.dto.reservation.ReserveStockRequest;
import com.ecommerce.inventoryservice.dto.reservation.BulkReserveStockRequest;
import com.ecommerce.inventoryservice.dto.reservation.BulkReservationResponse;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
import com.ecommerce.inventoryservice.exception.ReservationNotFoundException;
import com.ecommerce.inventoryservice.kafka.InventoryEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private final StockReservationRepository stockReservationRepository;
    private final InventoryEventProducer eventProducer;

    // Reservation expiry time in minutes (15 minutes)
    private static final long RESERVATION_EXPIRY_MINUTES = 15;

    /**
     * Reserve stock for an order. Uses atomic SQL queries to prevent race conditions.
     * Creates ACTIVE reservations and atomically deducts from availableStock.
     *
     * SKUs are sorted alphabetically before locking to prevent deadlocks
     * when multiple threads reserve overlapping SKUs.
     *
     * @param orderId Order ID
     * @param items List of items to reserve (SKU + quantity pairs)
     * @return List of created stock reservations
     * @throws InsufficientStockException if stock not available for any item
     * @throws InventoryNotFoundException if inventory not found for SKU
     */
    @Transactional
    public List<StockReservation> reserveStock(String orderId, List<BulkStockCheckRequest.StockCheckItem> items) {
        log.info("Attempting to reserve stock for order: {} with {} items", orderId, items.size());

        // Sort SKUs alphabetically to prevent deadlocks
        List<BulkStockCheckRequest.StockCheckItem> sortedItems = items.stream()
                .sorted((a, b) -> a.sku().compareTo(b.sku()))
                .collect(Collectors.toList());

        List<StockReservation> createdReservations = new ArrayList<>();

        for (BulkStockCheckRequest.StockCheckItem item : sortedItems) {
            // Look up inventory (read-only, for ID and event data)
            Inventory inventory = inventoryRepository.findBySku(item.sku())
                    .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for SKU: " + item.sku()));

            // Atomic reserve — single SQL statement, no race condition
            int updated = inventoryRepository.atomicReserveStock(inventory.getId(), item.requestedQuantity());

            if (updated == 0) {
                log.warn("Insufficient stock for SKU: {}. Requested: {}",
                        item.sku(), item.requestedQuantity());
                throw new InsufficientStockException(
                        item.sku(), item.requestedQuantity(), inventory.getAvailableStock()
                );
            }

            // Create reservation record
            StockReservation reservation = new StockReservation();
            reservation.setInventoryId(inventory.getId());
            reservation.setOrderId(orderId);
            reservation.setQuantity(item.requestedQuantity());
            reservation.setStatus(ReservationStatus.ACTIVE);
            reservation.setExpiresAt(Instant.now().plusSeconds(RESERVATION_EXPIRY_MINUTES * 60));

            StockReservation savedReservation = stockReservationRepository.save(reservation);
            createdReservations.add(savedReservation);

            log.info("Reserved {} units of SKU: {} for order: {}",
                    item.requestedQuantity(), item.sku(), orderId);

            // Publish event
            eventProducer.publishStockReserved(inventory.getId(), inventory.getSku(), inventory.getVariantId(),
                    inventory.getProductId(), item.requestedQuantity(), orderId);
        }

        log.info("Successfully reserved stock for order: {}", orderId);
        return createdReservations;
    }

    /**
     * Release a reservation (called on payment failure or order cancellation).
     * Uses atomic queries for both reservation status update and stock restoration.
     *
     * @param reservationId ID of the reservation to release
     * @param reason Reason for release (e.g., "PAYMENT_FAILED", "ORDER_CANCELLED")
     * @throws ReservationNotFoundException if reservation not found
     */
    @Transactional
    public void releaseReservationInternal(UUID reservationId, String reason) {
        log.info("Releasing reservation with ID: {} due to: {}", reservationId, reason);

        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));

        // Atomic release reservation status
        int reservationUpdated = stockReservationRepository.atomicReleaseReservation(reservationId, Instant.now());
        if (reservationUpdated == 0) {
            log.warn("Reservation {} already released/fulfilled, skipping", reservationId);
            return; // Idempotent — already processed
        }

        // Atomic restore stock
        int stockUpdated = inventoryRepository.atomicReleaseStock(reservation.getInventoryId(), reservation.getQuantity());
        if (stockUpdated == 0) {
            log.error("Failed to restore stock for reservation: {}. Inventory may be inconsistent.", reservationId);
        }

        // Fetch inventory for event publishing
        Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));

        log.info("Released {} units of SKU: {} for reservation: {} due to: {}",
                reservation.getQuantity(), inventory.getSku(), reservationId, reason);

        // Publish event
        eventProducer.publishStockReleased(inventory.getId(), inventory.getSku(), inventory.getVariantId(),
                inventory.getProductId(), reservation.getQuantity(), reservation.getOrderId(), reason);
    }

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

        List<BulkStockCheckResponse.StockCheckResult> results = request.items().stream()
            .map(item -> {
                Inventory inventory = inventoryRepository.findBySku(item.sku()).orElse(null);

                if (inventory == null) {
                    // SKU doesn't exist
                    return new BulkStockCheckResponse.StockCheckResult(
                        item.sku(),
                        item.requestedQuantity(),
                        0,
                        false,
                        item.requestedQuantity()
                    );
                }

                int availableQuantity = inventory.getAvailableStock();
                boolean isAvailable = availableQuantity >= item.requestedQuantity();
                int shortfall = isAvailable ? 0 : item.requestedQuantity() - availableQuantity;

                return new BulkStockCheckResponse.StockCheckResult(
                    item.sku(),
                    item.requestedQuantity(),
                    availableQuantity,
                    isAvailable,
                    shortfall
                );
            })
            .collect(Collectors.toList());

        boolean allAvailable = results.stream().allMatch(BulkStockCheckResponse.StockCheckResult::isAvailable);

        log.info("Availability check completed. All available: {}", allAvailable);
        return new BulkStockCheckResponse(allAvailable, results);
    }

    /**
     * Get all reservations for an order.
     *
     * @param orderId Order ID
     * @return List of reservations for the order
     */
    @Transactional(readOnly = true)
    public List<StockReservation> getOrderReservations(String orderId) {
        return stockReservationRepository.findByOrderId(orderId);
    }

    /**
     * Get a specific reservation by ID.
     *
     * @param reservationId Reservation ID
     * @return The reservation
     * @throws ReservationNotFoundException if reservation not found
     */
    @Transactional(readOnly = true)
    public StockReservation getReservationById(UUID reservationId) {
        return stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));
    }

    /**
     * Get all active reservations (ACTIVE or RESERVED status).
     *
     * @return List of active reservations
     */
    @Transactional(readOnly = true)
    public List<StockReservation> getActiveReservations() {
        return stockReservationRepository.findByStatusIn(
                List.of(ReservationStatus.ACTIVE, ReservationStatus.RESERVED)
        );
    }

    /**
     * Get all expired reservations.
     *
     * @return List of expired reservations
     */
    @Transactional(readOnly = true)
    public List<StockReservation> getExpiredReservations() {
        return stockReservationRepository.findByExpiresAtBefore(Instant.now())
                .stream()
                .filter(r -> !ReservationStatus.RELEASED.equals(r.getStatus())
                        && !ReservationStatus.FULFILLED.equals(r.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Clean up expired reservations by releasing them and restoring stock.
     * Uses a two-step approach:
     * 1. Find expired ACTIVE reservations (before atomically expiring them)
     * 2. For each, atomically release the stock
     * 3. Atomically mark all as EXPIRED in batch
     *
     * @return Number of reservations cleaned up
     */
    @Transactional
    public int cleanupExpiredReservations() {
        log.info("Starting cleanup of expired reservations");

        Instant now = Instant.now();

        // Find expired reservations first (for stock restoration)
        List<StockReservation> expiredReservations = stockReservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, now);

        if (expiredReservations.isEmpty()) {
            log.info("No expired reservations found");
            return 0;
        }

        int cleanedCount = 0;

        // Restore stock for each expired reservation
        for (StockReservation reservation : expiredReservations) {
            try {
                int stockUpdated = inventoryRepository.atomicReleaseStock(
                        reservation.getInventoryId(), reservation.getQuantity());
                if (stockUpdated > 0) {
                    cleanedCount++;
                    log.info("Restored {} units for expired reservation: {}",
                            reservation.getQuantity(), reservation.getId());
                }
            } catch (Exception e) {
                log.error("Failed to restore stock for expired reservation: {}", reservation.getId(), e);
            }
        }

        // Atomically mark all expired reservations as EXPIRED in batch
        int expiredCount = stockReservationRepository.atomicExpireReservations(now);
        log.info("Cleanup completed. {} reservations expired, {} stocks restored", expiredCount, cleanedCount);

        return cleanedCount;
    }

    // ========== DTO Response Methods for Controller ==========
    // These methods handle DTO conversion and are called directly by the controller

    /**
     * Reserve stock for a single item and return DTO response.
     * Controller calls this method directly.
     *
     * @param request Reserve stock request
     * @return Reservation response DTO
     */
    @Transactional
    public ReservationResponse reserveSingleStock(ReserveStockRequest request) {
        BulkStockCheckRequest.StockCheckItem item = new BulkStockCheckRequest.StockCheckItem(
                request.sku(),
                request.quantity()
        );
        List<StockReservation> reservations = reserveStock(request.orderId(), List.of(item));

        if (reservations.isEmpty()) {
            throw new IllegalStateException("Failed to create reservation");
        }

        return convertToReservationResponse(reservations.get(0));
    }

    /**
     * Reserve stock for multiple items and return DTO response.
     * Controller calls this method directly.
     *
     * @param request Bulk reserve stock request
     * @return Bulk reservation response DTO
     */
    @Transactional
    public BulkReservationResponse reserveBulkStock(BulkReserveStockRequest request) {
        try {
            List<BulkStockCheckRequest.StockCheckItem> items = request.items().stream()
                    .map(item -> new BulkStockCheckRequest.StockCheckItem(item.sku(), item.quantity()))
                    .collect(Collectors.toList());

            List<StockReservation> reservations = reserveStock(request.orderId(), items);
            List<ReservationResponse> responses = reservations.stream()
                    .map(this::convertToReservationResponse)
                    .collect(Collectors.toList());

            return new BulkReservationResponse(
                    true,
                    request.orderId(),
                    responses,
                    List.of()
            );
        } catch (InsufficientStockException | InventoryNotFoundException e) {
            log.error("Bulk reservation failed for order: {}", request.orderId(), e);
            return new BulkReservationResponse(
                    false,
                    request.orderId(),
                    List.of(),
                    List.of()
            );
        }
    }

    /**
     * Confirm a pending reservation and return DTO response.
     * Uses atomic query — only ACTIVE reservations can be confirmed.
     *
     * @param reservationId Reservation ID
     * @return Updated reservation response DTO
     */
    @Transactional
    public ReservationResponse confirmReservation(UUID reservationId) {
        // Atomic confirm — only succeeds if status is ACTIVE
        int updated = stockReservationRepository.atomicConfirmReservation(reservationId);

        if (updated == 0) {
            // Check if reservation exists at all
            StockReservation reservation = stockReservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));
            throw new IllegalStateException("Can only confirm ACTIVE reservations. Current status: " + reservation.getStatus());
        }

        // Re-fetch for response
        StockReservation confirmed = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));

        Inventory inventory = inventoryRepository.findById(confirmed.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));
        eventProducer.publishStockReserved(inventory.getId(), inventory.getSku(), inventory.getVariantId(),
                inventory.getProductId(), confirmed.getQuantity(), confirmed.getOrderId());

        return convertToReservationResponse(confirmed);
    }

    /**
     * Release a reservation and return DTO response.
     * Uses atomic queries for both reservation status and stock restoration.
     *
     * @param reservationId Reservation ID
     * @param reason Reason for release
     * @return Updated reservation response DTO
     */
    @Transactional
    public ReservationResponse releaseReservation(UUID reservationId, String reason) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));

        // Atomic release reservation status
        int reservationUpdated = stockReservationRepository.atomicReleaseReservation(reservationId, Instant.now());
        if (reservationUpdated == 0) {
            log.warn("Reservation {} already released/fulfilled, skipping", reservationId);
            return convertToReservationResponse(reservation);
        }

        // Atomic restore stock
        inventoryRepository.atomicReleaseStock(reservation.getInventoryId(), reservation.getQuantity());

        // Fetch inventory for event publishing
        Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));

        String finalReason = reason != null && !reason.isBlank() ? reason : "Manual release";
        log.info("Released {} units of SKU: {} for reservation: {} due to: {}",
                reservation.getQuantity(), inventory.getSku(), reservationId, finalReason);

        eventProducer.publishStockReleased(inventory.getId(), inventory.getSku(), inventory.getVariantId(),
                inventory.getProductId(), reservation.getQuantity(), reservation.getOrderId(), finalReason);

        // Re-fetch for updated response
        StockReservation updated = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));
        return convertToReservationResponse(updated);
    }

    /**
     * Fulfill a reservation — stock leaves the system (shipped to customer).
     * Uses atomic queries: deducts from reservedStock, marks reservation as FULFILLED.
     * Only RESERVED (confirmed) reservations can be fulfilled.
     *
     * @param reservationId Reservation ID
     * @return Updated reservation response DTO
     */
    @Transactional
    public ReservationResponse fulfillReservation(UUID reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));

        // Atomic fulfill — only succeeds if status is RESERVED
        int reservationUpdated = stockReservationRepository.atomicFulfillReservation(reservationId, Instant.now());
        if (reservationUpdated == 0) {
            throw new IllegalStateException("Can only fulfill RESERVED reservations. Current status: " + reservation.getStatus());
        }

        // Atomic confirm stock — deducts from reservedStock (stock leaves the system)
        int stockUpdated = inventoryRepository.atomicConfirmStock(reservation.getInventoryId(), reservation.getQuantity());
        if (stockUpdated == 0) {
            log.error("Failed to deduct reserved stock for reservation: {}. Inventory may be inconsistent.", reservationId);
        }

        Inventory inventory = inventoryRepository.findById(reservation.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));

        log.info("Fulfilled reservation: {} — {} units of SKU: {} shipped for order: {}",
                reservationId, reservation.getQuantity(), inventory.getSku(), reservation.getOrderId());

        // Re-fetch for updated response
        StockReservation fulfilled = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));
        return convertToReservationResponse(fulfilled);
    }

    /**
     * Get all reservations for an order as DTOs.
     * Controller calls this method directly.
     *
     * @param orderId Order ID
     * @return List of reservation response DTOs
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getOrderReservationResponses(String orderId) {
        return getOrderReservations(orderId).stream()
                .map(this::convertToReservationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific reservation as DTO.
     * Controller calls this method directly.
     *
     * @param reservationId Reservation ID
     * @return Reservation response DTO
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservationResponse(UUID reservationId) {
        StockReservation reservation = getReservationById(reservationId);
        return convertToReservationResponse(reservation);
    }

    /**
     * Get all active reservations as DTOs.
     * Controller calls this method directly.
     *
     * @return List of active reservation response DTOs
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getActiveReservationResponses() {
        return getActiveReservations().stream()
                .map(this::convertToReservationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all expired reservations as DTOs.
     * Controller calls this method directly.
     *
     * @return List of expired reservation response DTOs
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getExpiredReservationResponses() {
        return getExpiredReservations().stream()
                .map(this::convertToReservationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert StockReservation entity to ReservationResponse DTO.
     *
     * @param reservation Stock reservation entity
     * @return Reservation response DTO
     */
    private ReservationResponse convertToReservationResponse(StockReservation reservation) {
        // Fetch the SKU from the Inventory entity
        String sku = inventoryRepository.findById(reservation.getInventoryId())
                .map(Inventory::getSku)
                .orElse("UNKNOWN");

        return new ReservationResponse(
                reservation.getId(),
                sku,
                reservation.getOrderId(),
                reservation.getQuantity(),
                reservation.getStatus().toString(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                reservation.getFulfilledAt(),
                reservation.getCancelledAt()
        );
    }
}
