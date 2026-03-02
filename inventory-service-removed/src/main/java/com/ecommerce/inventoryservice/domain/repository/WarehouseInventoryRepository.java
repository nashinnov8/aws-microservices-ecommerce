package com.ecommerce.inventoryservice.domain.repository;

import com.ecommerce.inventoryservice.domain.entity.WarehouseInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for WarehouseInventory entity operations.
 * Manages the mapping between warehouses and inventory items.
 */
@Repository
public interface WarehouseInventoryRepository extends JpaRepository<WarehouseInventory, UUID> {

    /**
     * Find all inventory items in a specific warehouse.
     *
     * @param warehouseId The warehouse ID
     * @return List of warehouse inventory mappings
     */
    List<WarehouseInventory> findByWarehouseId(UUID warehouseId);

    /**
     * Find all warehouse locations for a specific inventory item.
     *
     * @param inventoryId The inventory ID
     * @return List of warehouse inventory mappings
     */
    List<WarehouseInventory> findByInventoryId(UUID inventoryId);

    /**
     * Find specific warehouse-inventory mapping.
     *
     * @param warehouseId The warehouse ID
     * @param inventoryId The inventory ID
     * @return Optional containing the mapping if found
     */
    Optional<WarehouseInventory> findByWarehouseIdAndInventoryId(UUID warehouseId, UUID inventoryId);

    /**
     * Find specific warehouse-inventory mapping with pessimistic lock.
     * Use this when updating quantities to prevent race conditions.
     *
     * @param warehouseId The warehouse ID
     * @param inventoryId The inventory ID
     * @return Optional containing the mapping if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wi FROM WarehouseInventory wi WHERE wi.warehouseId = :warehouseId AND wi.inventoryId = :inventoryId")
    Optional<WarehouseInventory> findByWarehouseIdAndInventoryIdForUpdate(
            @Param("warehouseId") UUID warehouseId,
            @Param("inventoryId") UUID inventoryId);

    /**
     * Check if mapping exists between warehouse and inventory.
     *
     * @param warehouseId The warehouse ID
     * @param inventoryId The inventory ID
     * @return true if mapping exists, false otherwise
     */
    boolean existsByWarehouseIdAndInventoryId(UUID warehouseId, UUID inventoryId);

    /**
     * Delete all mappings for a specific warehouse.
     *
     * @param warehouseId The warehouse ID
     */
    void deleteByWarehouseId(UUID warehouseId);

    /**
     * Delete all mappings for a specific inventory item.
     *
     * @param inventoryId The inventory ID
     */
    void deleteByInventoryId(UUID inventoryId);

    /**
     * Calculate total quantity across all warehouses for an inventory item.
     *
     * @param inventoryId The inventory ID
     * @return Total quantity across all warehouses
     */
    @Query("SELECT COALESCE(SUM(wi.quantity), 0) FROM WarehouseInventory wi WHERE wi.inventoryId = :inventoryId")
    int getTotalQuantityByInventoryId(@Param("inventoryId") UUID inventoryId);

    /**
     * Find all warehouse inventory mappings with quantity greater than zero.
     *
     * @param inventoryId The inventory ID
     * @return List of warehouse inventory mappings with stock
     */
    @Query("SELECT wi FROM WarehouseInventory wi WHERE wi.inventoryId = :inventoryId AND wi.quantity > 0")
    List<WarehouseInventory> findByInventoryIdWithStock(@Param("inventoryId") UUID inventoryId);
}
