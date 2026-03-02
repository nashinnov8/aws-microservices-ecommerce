package com.ecommerce.inventoryservice.domain.repository;

import com.ecommerce.inventoryservice.domain.entity.StockReservation;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for StockReservation entity operations.
 * Provides methods for managing stock reservations for orders.
 */
@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /**
     * Find all reservations for a specific inventory item.
     *
     * @param inventoryId The inventory ID
     * @return List of stock reservations
     */
    List<StockReservation> findByInventoryId(UUID inventoryId);

    /**
     * Find all active reservations for a specific inventory item.
     *
     * @param inventoryId The inventory ID
     * @return List of active stock reservations
     */
    List<StockReservation> findByInventoryIdAndStatus(UUID inventoryId, ReservationStatus status);

    /**
     * Find all reservations for a specific order.
     *
     * @param orderId The order ID
     * @return List of stock reservations for the order
     */
    List<StockReservation> findByOrderId(String orderId);

    /**
     * Find a specific reservation with pessimistic lock.
     * Use this when fulfilling or releasing reservations.
     *
     * @param id The reservation ID
     * @return Optional containing the reservation if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sr FROM StockReservation sr WHERE sr.id = :id")
    Optional<StockReservation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Find all expired reservations that are still active.
     * Used by the scheduler to release expired reservations.
     *
     * @param status The status to filter by (typically ACTIVE)
     * @param expirationTime The time threshold (find reservations expired before this)
     * @return List of expired active reservations
     */
    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant expirationTime);

    /**
     * Find all expired active reservations with pessimistic lock.
     * Used by the scheduler for batch expiration.
     *
     * @param expirationTime The time threshold
     * @return List of expired active reservations
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sr FROM StockReservation sr WHERE sr.status = 'ACTIVE' AND sr.expiresAt < :expirationTime")
    List<StockReservation> findExpiredReservationsForUpdate(@Param("expirationTime") Instant expirationTime);

    /**
     * Check if an active reservation exists for an order and inventory item.
     *
     * @param orderId The order ID
     * @param inventoryId The inventory ID
     * @return true if active reservation exists
     */
    boolean existsByOrderIdAndInventoryIdAndStatus(String orderId, UUID inventoryId, ReservationStatus status);

    /**
     * Find active reservation for a specific order and inventory item.
     *
     * @param orderId The order ID
     * @param inventoryId The inventory ID
     * @return Optional containing the reservation if found
     */
    Optional<StockReservation> findByOrderIdAndInventoryIdAndStatus(
            String orderId, UUID inventoryId, ReservationStatus status);

    /**
     * Count active reservations for an inventory item.
     *
     * @param inventoryId The inventory ID
     * @return Number of active reservations
     */
    long countByInventoryIdAndStatus(UUID inventoryId, ReservationStatus status);

    /**
     * Get total reserved quantity for an inventory item.
     *
     * @param inventoryId The inventory ID
     * @return Total reserved quantity
     */
    @Query("SELECT COALESCE(SUM(sr.quantity), 0) FROM StockReservation sr WHERE sr.inventoryId = :inventoryId AND sr.status = 'ACTIVE'")
    int getTotalReservedQuantityByInventoryId(@Param("inventoryId") UUID inventoryId);

    /**
     * Find all reservations with pagination.
     *
     * @param pageable Pagination information
     * @return Page of reservations
     */
    Page<StockReservation> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find reservations by status with pagination.
     *
     * @param status The reservation status
     * @param pageable Pagination information
     * @return Page of reservations
     */
    Page<StockReservation> findByStatusOrderByCreatedAtDesc(ReservationStatus status, Pageable pageable);

    /**
     * Find reservations expiring within a time range.
     * Useful for notifications about soon-to-expire reservations.
     *
     * @param startTime Start of time range
     * @param endTime End of time range
     * @return List of reservations expiring within the range
     */
    @Query("SELECT sr FROM StockReservation sr WHERE sr.status = 'ACTIVE' AND sr.expiresAt BETWEEN :startTime AND :endTime")
    List<StockReservation> findReservationsExpiringSoon(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Find all reservations with any of the given statuses.
     *
     * @param statuses List of statuses to filter by
     * @return List of reservations with specified statuses
     */
    List<StockReservation> findByStatusIn(List<ReservationStatus> statuses);

    /**
     * Find all reservations that have expired (expiresAt is before given time).
     *
     * @param time The time threshold
     * @return List of reservations that expired before the given time
     */
    List<StockReservation> findByExpiresAtBefore(Instant time);

    // ==================== ATOMIC SQL OPERATIONS ====================

    /**
     * Atomically update reservation status from one state to another.
     * WHERE clause ensures only reservations in the expected state are changed (prevents double-processing).
     *
     * @param reservationId The reservation ID
     * @param currentStatus The expected current status
     * @param newStatus The target status
     * @return Number of rows updated (0 if status mismatch or not found)
     */
    @Modifying
    @Query("UPDATE StockReservation r SET r.status = :newStatus, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :reservationId AND r.status = :currentStatus")
    int atomicUpdateStatus(@Param("reservationId") UUID reservationId,
                           @Param("currentStatus") ReservationStatus currentStatus,
                           @Param("newStatus") ReservationStatus newStatus);

    /**
     * Atomically confirm a reservation (set status to RESERVED, set fulfilledAt).
     *
     * @param reservationId The reservation ID
     * @return Number of rows updated (0 if not ACTIVE or not found)
     */
    @Modifying
    @Query("UPDATE StockReservation r SET r.status = 'RESERVED', r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :reservationId AND r.status = 'ACTIVE'")
    int atomicConfirmReservation(@Param("reservationId") UUID reservationId);

    /**
     * Atomically release a reservation (set status to RELEASED, set cancelledAt).
     *
     * @param reservationId The reservation ID
     * @param now Current timestamp for cancelledAt
     * @return Number of rows updated (0 if already released/fulfilled)
     */
    @Modifying
    @Query("UPDATE StockReservation r SET r.status = 'RELEASED', r.cancelledAt = :now, " +
           "r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :reservationId AND r.status IN ('ACTIVE', 'RESERVED')")
    int atomicReleaseReservation(@Param("reservationId") UUID reservationId, @Param("now") Instant now);

    /**
     * Atomically fulfill a reservation (set status to FULFILLED, set fulfilledAt).
     *
     * @param reservationId The reservation ID
     * @param now Current timestamp for fulfilledAt
     * @return Number of rows updated
     */
    @Modifying
    @Query("UPDATE StockReservation r SET r.status = 'FULFILLED', r.fulfilledAt = :now, " +
           "r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :reservationId AND r.status = 'RESERVED'")
    int atomicFulfillReservation(@Param("reservationId") UUID reservationId, @Param("now") Instant now);

    /**
     * Atomically expire reservations that have passed their expiry time.
     *
     * @param now Current timestamp
     * @return Number of rows updated
     */
    @Modifying
    @Query("UPDATE StockReservation r SET r.status = 'EXPIRED', r.cancelledAt = :now, " +
           "r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    int atomicExpireReservations(@Param("now") Instant now);
}
