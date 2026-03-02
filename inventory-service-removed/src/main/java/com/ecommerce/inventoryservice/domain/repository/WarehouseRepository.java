package com.ecommerce.inventoryservice.domain.repository;

import com.ecommerce.inventoryservice.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Warehouse entity operations.
 * Provides methods for warehouse management.
 */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    /**
     * Find warehouse by its unique code.
     *
     * @param warehouseCode The warehouse code (e.g., "WH-SYD-01")
     * @return Optional containing the warehouse if found
     */
    Optional<Warehouse> findByWarehouseCode(String warehouseCode);

    /**
     * Check if a warehouse exists with the given code.
     *
     * @param warehouseCode The warehouse code to check
     * @return true if warehouse exists, false otherwise
     */
    boolean existsByWarehouseCode(String warehouseCode);

    /**
     * Find all active warehouses.
     *
     * @return List of active warehouses
     */
    List<Warehouse> findByIsActiveTrue();

    /**
     * Find all warehouses in a specific city.
     *
     * @param city The city name
     * @return List of warehouses in the city
     */
    List<Warehouse> findByCityAndIsActiveTrue(String city);

    /**
     * Find all warehouses in a specific country.
     *
     * @param country The country name
     * @return List of warehouses in the country
     */
    List<Warehouse> findByCountryAndIsActiveTrue(String country);

    /**
     * Find warehouse by code and ensure it's active.
     *
     * @param warehouseCode The warehouse code
     * @return Optional containing the warehouse if found and active
     */
    Optional<Warehouse> findByWarehouseCodeAndIsActiveTrue(String warehouseCode);
}
