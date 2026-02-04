package com.ecommerce.inventoryservice.domain.repository;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
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
}
