package com.ecommerce.inventoryservice.domain.repository;

import com.ecommerce.inventoryservice.domain.entity.StockMovement;
import com.ecommerce.inventoryservice.domain.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for StockMovement entity operations.
 * Provides methods for querying stock movement history (audit trail).
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /**
     * Find all movements for a specific inventory item.
     *
     * @param inventoryId The inventory ID
     * @return List of stock movements
     */
    List<StockMovement> findByInventoryId(UUID inventoryId);

    /**
     * Find all movements for a specific inventory item with pagination.
     *
     * @param inventoryId The inventory ID
     * @param pageable Pagination information
     * @return Page of stock movements
     */
    Page<StockMovement> findByInventoryIdOrderByPerformedAtDesc(UUID inventoryId, Pageable pageable);

    /**
     * Find movements for an inventory item within a time range.
     *
     * @param inventoryId The inventory ID
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @return List of stock movements within the time range
     */
    List<StockMovement> findByInventoryIdAndPerformedAtBetween(
            UUID inventoryId, Instant startTime, Instant endTime);

    /**
     * Find movements for an inventory item within a time range with pagination.
     *
     * @param inventoryId The inventory ID
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @param pageable Pagination information
     * @return Page of stock movements
     */
    Page<StockMovement> findByInventoryIdAndPerformedAtBetweenOrderByPerformedAtDesc(
            UUID inventoryId, Instant startTime, Instant endTime, Pageable pageable);

    /**
     * Find all movements of a specific type.
     *
     * @param movementType The type of movement
     * @return List of stock movements
     */
    List<StockMovement> findByMovementType(MovementType movementType);

    /**
     * Find movements of a specific type with pagination.
     *
     * @param movementType The type of movement
     * @param pageable Pagination information
     * @return Page of stock movements
     */
    Page<StockMovement> findByMovementTypeOrderByPerformedAtDesc(MovementType movementType, Pageable pageable);

    /**
     * Find movements by reference ID (e.g., order ID, transfer ID).
     *
     * @param referenceId The reference ID
     * @return List of stock movements with that reference
     */
    List<StockMovement> findByReferenceId(String referenceId);

    /**
     * Find movements for a specific warehouse.
     *
     * @param warehouseId The warehouse ID
     * @return List of stock movements
     */
    List<StockMovement> findByWarehouseId(UUID warehouseId);

    /**
     * Find movements for a specific warehouse with pagination.
     *
     * @param warehouseId The warehouse ID
     * @param pageable Pagination information
     * @return Page of stock movements
     */
    Page<StockMovement> findByWarehouseIdOrderByPerformedAtDesc(UUID warehouseId, Pageable pageable);

    /**
     * Find movements performed by a specific user.
     *
     * @param performedBy The user identifier
     * @return List of stock movements
     */
    List<StockMovement> findByPerformedBy(String performedBy);

    /**
     * Get the most recent movements across all inventory.
     *
     * @param pageable Pagination information
     * @return Page of recent stock movements
     */
    Page<StockMovement> findAllByOrderByPerformedAtDesc(Pageable pageable);

    /**
     * Count movements for an inventory item.
     *
     * @param inventoryId The inventory ID
     * @return Number of movements
     */
    long countByInventoryId(UUID inventoryId);

    /**
     * Calculate total quantity moved for an inventory item by movement type.
     *
     * @param inventoryId The inventory ID
     * @param movementType The movement type
     * @return Total quantity
     */
    @Query("SELECT COALESCE(SUM(sm.quantity), 0) FROM StockMovement sm WHERE sm.inventoryId = :inventoryId AND sm.movementType = :movementType")
    int getTotalQuantityByInventoryIdAndMovementType(
            @Param("inventoryId") UUID inventoryId,
            @Param("movementType") MovementType movementType);
}
