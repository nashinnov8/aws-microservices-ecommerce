package com.ecommerce.inventoryservice.domain.repository;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Inventory entity operations.
 * Provides methods for SKU-based lookups and stock management.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * Find inventory by SKU.
     * SKU is the primary business key for all inventory operations.
     *
     * @param sku The SKU to search for
     * @return Optional containing the inventory if found
     */
    Optional<Inventory> findBySku(String sku);

    /**
     * Find inventory by SKU with pessimistic lock for concurrent updates.
     * Use this method when updating stock to prevent race conditions.
     *
     * @param sku The SKU to search for
     * @return Optional containing the inventory if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.sku = :sku")
    Optional<Inventory> findBySkuForUpdate(@Param("sku") String sku);

    /**
     * Find inventory by variant ID.
     *
     * @param variantId The variant ID from product-service
     * @return Optional containing the inventory if found
     */
    Optional<Inventory> findByVariantId(UUID variantId);

    /**
     * Find inventory by variant ID with pessimistic lock.
     *
     * @param variantId The variant ID from product-service
     * @return Optional containing the inventory if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variantId = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(@Param("variantId") UUID variantId);

    /**
     * Find all inventory records for a product.
     * Returns list of all variant inventories for a single product.
     *
     * @param productId The product ID from product-service
     * @return List of inventory records for all variants of the product
     */
    List<Inventory> findByProductId(UUID productId);

    /**
     * Find all active inventory records for a product.
     *
     * @param productId The product ID from product-service
     * @return List of active inventory records for the product
     */
    List<Inventory> findByProductIdAndIsActiveTrue(UUID productId);

    /**
     * Check if inventory exists for a SKU.
     *
     * @param sku The SKU to check
     * @return true if inventory exists, false otherwise
     */
    boolean existsBySku(String sku);

    /**
     * Check if inventory exists for a variant ID.
     *
     * @param variantId The variant ID to check
     * @return true if inventory exists, false otherwise
     */
    boolean existsByVariantId(UUID variantId);

    /**
     * Find all items that are at or below their reorder point.
     * Used for low stock alerts and reporting.
     *
     * @return List of inventory items needing reorder
     */
    @Query("SELECT i FROM Inventory i WHERE i.isActive = true AND i.availableStock <= i.reorderPoint")
    List<Inventory> findLowStockItems();

    /**
     * Find all items that are below minimum stock level.
     *
     * @return List of inventory items below minimum
     */
    @Query("SELECT i FROM Inventory i WHERE i.isActive = true AND i.availableStock <= i.minStockLevel")
    List<Inventory> findBelowMinimumStockItems();

    /**
     * Find all active inventory records.
     *
     * @return List of all active inventory records
     */
    List<Inventory> findByIsActiveTrue();

    /**
     * Find inventory by SKU and ensure it's active.
     *
     * @param sku The SKU to search for
     * @return Optional containing the inventory if found and active
     */
    Optional<Inventory> findBySkuAndIsActiveTrue(String sku);

    /**
     * Find inventory by ID with pessimistic lock for concurrent updates.
     * Use this method when updating stock by ID to prevent race conditions.
     *
     * @param id The inventory ID
     * @return Optional containing the inventory if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") UUID id);

    // ==================== ATOMIC SQL OPERATIONS ====================
    // These bypass fetch-modify-save and execute as single SQL statements.
    // The database handles concurrency — no race conditions possible.
    // Return value = number of rows updated (0 means WHERE condition failed).

    /**
     * Atomically add stock to an inventory item.
     * Used for receiving goods, restocking operations.
     *
     * @param sku The SKU to add stock to
     * @param quantity The quantity to add (must be positive)
     * @return Number of rows updated (0 if SKU not found or inactive)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :quantity, " +
           "i.updatedAt = CURRENT_TIMESTAMP WHERE i.sku = :sku AND i.isActive = true")
    int atomicAddStock(@Param("sku") String sku, @Param("quantity") int quantity);

    /**
     * Atomically deduct stock from an inventory item.
     * WHERE clause ensures available_stock >= quantity (prevents negative stock).
     *
     * @param sku The SKU to deduct from
     * @param quantity The quantity to deduct
     * @return Number of rows updated (0 if insufficient stock or SKU not found)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :quantity, " +
           "i.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE i.sku = :sku AND i.availableStock >= :quantity AND i.isActive = true")
    int atomicDeductStock(@Param("sku") String sku, @Param("quantity") int quantity);

    /**
     * Atomically reserve stock (move from available to reserved).
     * Single SQL statement — no race condition between read and write.
     *
     * @param inventoryId The inventory ID
     * @param quantity The quantity to reserve
     * @return Number of rows updated (0 if insufficient stock)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :quantity, " +
           "i.reservedStock = i.reservedStock + :quantity, " +
           "i.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE i.id = :inventoryId AND i.availableStock >= :quantity AND i.isActive = true")
    int atomicReserveStock(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

    /**
     * Atomically release reserved stock (move from reserved back to available).
     * Used when order is cancelled or reservation expires.
     *
     * @param inventoryId The inventory ID
     * @param quantity The quantity to release
     * @return Number of rows updated (0 if insufficient reserved stock)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :quantity, " +
           "i.reservedStock = i.reservedStock - :quantity, " +
           "i.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE i.id = :inventoryId AND i.reservedStock >= :quantity")
    int atomicReleaseStock(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

    /**
     * Atomically confirm reserved stock (deduct from reserved — stock leaves the system).
     * Used when order is fulfilled/shipped.
     *
     * @param inventoryId The inventory ID
     * @param quantity The quantity to confirm
     * @return Number of rows updated (0 if insufficient reserved stock)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedStock = i.reservedStock - :quantity, " +
           "i.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE i.id = :inventoryId AND i.reservedStock >= :quantity")
    int atomicConfirmStock(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

    /**
     * Atomically deactivate an inventory item by SKU.
     *
     * @param sku The SKU to deactivate
     * @return Number of rows updated
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.isActive = false, i.updatedAt = CURRENT_TIMESTAMP WHERE i.sku = :sku")
    int atomicDeactivate(@Param("sku") String sku);
}
