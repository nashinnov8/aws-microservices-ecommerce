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
     * Reserve stock for an order. Uses pessimistic locking to prevent race conditions.
     * Creates PENDING reservations and deducts from availableStock.
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

        List<StockReservation> createdReservations = new ArrayList<>();

        // First, check all items are available (fail-fast)
        for (BulkStockCheckRequest.StockCheckItem item : items) {
            Inventory inventory = inventoryRepository.findBySkuForUpdate(item.sku())
                    .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for SKU: " + item.sku()));

            int availableForReservation = inventory.getAvailableStock();
            if (availableForReservation < item.requestedQuantity()) {
                log.warn("Insufficient stock for SKU: {}. Available: {}, Requested: {}",
                    item.sku(), availableForReservation, item.requestedQuantity());
                throw new InsufficientStockException(
                    "Insufficient stock for SKU: " + item.sku() +
                    ". Available: " + availableForReservation +
                    ", Requested: " + item.requestedQuantity()
                );
            }
        }

        // All checks passed, proceed with reservation
        for (BulkStockCheckRequest.StockCheckItem item : items) {
            Inventory inventory = inventoryRepository.findBySkuForUpdate(item.sku())
                    .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for SKU: " + item.sku()));

            // Create reservation
            StockReservation reservation = new StockReservation();
            reservation.setInventoryId(inventory.getId());
            reservation.setOrderId(orderId);
            reservation.setQuantity(item.requestedQuantity());
            reservation.setStatus(ReservationStatus.ACTIVE);

            // Set expiry time to 15 minutes from now
            reservation.setExpiresAt(Instant.now().plusSeconds(RESERVATION_EXPIRY_MINUTES * 60));

            StockReservation savedReservation = stockReservationRepository.save(reservation);
            createdReservations.add(savedReservation);

            // Deduct from available stock
            inventory.setAvailableStock(inventory.getAvailableStock() - item.requestedQuantity());
            inventory.setReservedStock(inventory.getReservedStock() + item.requestedQuantity());
            inventoryRepository.save(inventory);

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
     * Changes status to RELEASED and restores availableStock.
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

        Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));

        // Update reservation status
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setCancelledAt(Instant.now());
        stockReservationRepository.save(reservation);

        // Restore stock
        inventory.setAvailableStock(inventory.getAvailableStock() + reservation.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());
        inventoryRepository.save(inventory);

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
     * This should be called periodically (e.g., by a scheduled task).
     *
     * @return Number of reservations cleaned up
     */
    @Transactional
    public int cleanupExpiredReservations() {
        log.info("Starting cleanup of expired reservations");

        List<StockReservation> expiredReservations = getExpiredReservations();
        int cleanedCount = 0;

        for (StockReservation reservation : expiredReservations) {
            try {
                releaseReservationInternal(reservation.getId(), "RESERVATION_EXPIRED");
                cleanedCount++;
            } catch (Exception e) {
                log.error("Failed to cleanup expired reservation: {}", reservation.getId(), e);
            }
        }

        log.info("Cleanup completed. {} reservations were cleaned up", cleanedCount);
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
     * Controller calls this method directly.
     *
     * @param reservationId Reservation ID
     * @return Updated reservation response DTO
     */
    @Transactional
    public ReservationResponse confirmReservation(UUID reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));

        if (!ReservationStatus.ACTIVE.equals(reservation.getStatus())) {
            throw new IllegalStateException("Can only confirm ACTIVE reservations. Current status: " + reservation.getStatus());
        }

        reservation.setStatus(ReservationStatus.RESERVED);
        StockReservation updated = stockReservationRepository.save(reservation);

        Inventory inventory = inventoryRepository.findById(updated.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));
        eventProducer.publishStockReserved(inventory.getId(), inventory.getSku(), inventory.getVariantId(),
                inventory.getProductId(), updated.getQuantity(), updated.getOrderId());

        return convertToReservationResponse(updated);
    }

    /**
     * Release a reservation and return DTO response.
     * Controller calls this method directly.
     *
     * @param reservationId Reservation ID
     * @param reason Reason for release
     * @return Updated reservation response DTO
     */
    @Transactional
    public ReservationResponse releaseReservation(UUID reservationId, String reason) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with ID: " + reservationId));

        Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setCancelledAt(Instant.now());
        StockReservation updated = stockReservationRepository.save(reservation);

        inventory.setAvailableStock(inventory.getAvailableStock() + reservation.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());
        inventoryRepository.save(inventory);

        String finalReason = reason != null && !reason.isBlank() ? reason : "Manual release";
        log.info("Released {} units of SKU: {} for reservation: {} due to: {}",
            reservation.getQuantity(), inventory.getSku(), reservationId, finalReason);

        eventProducer.publishStockReleased(inventory.getId(), inventory.getSku(), inventory.getVariantId(),
                inventory.getProductId(), reservation.getQuantity(), reservation.getOrderId(), finalReason);

        return convertToReservationResponse(updated);
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
