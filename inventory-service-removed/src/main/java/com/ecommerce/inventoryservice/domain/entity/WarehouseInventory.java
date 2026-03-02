package com.ecommerce.inventoryservice.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Entity for tracking stock distribution across warehouses.
 * Maps inventory items to specific warehouse locations with quantity and location details.
 */
@Entity
@Table(name = "warehouse_inventory",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouse_inventory", columnNames = {"warehouseId", "inventoryId"})
    },
    indexes = {
        @Index(name = "idx_wi_warehouse_id", columnList = "warehouseId"),
        @Index(name = "idx_wi_inventory_id", columnList = "inventoryId")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class WarehouseInventory extends BaseEntity {

    /**
     * Reference to the warehouse.
     */
    @NotNull(message = "Warehouse ID is required")
    @Column(nullable = false)
    private UUID warehouseId;

    /**
     * Reference to the inventory item.
     */
    @NotNull(message = "Inventory ID is required")
    @Column(nullable = false)
    private UUID inventoryId;

    /**
     * Quantity of this item in this warehouse.
     */
    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(nullable = false)
    private int quantity = 0;

    /**
     * Aisle location within the warehouse.
     */
    @Column(length = 20)
    private String aisle;

    /**
     * Rack location within the aisle.
     */
    @Column(length = 20)
    private String rack;

    /**
     * Shelf location within the rack.
     */
    @Column(length = 20)
    private String shelf;

    /**
     * Constructor for creating a warehouse inventory mapping.
     */
    public WarehouseInventory(UUID warehouseId, UUID inventoryId, int quantity) {
        this.warehouseId = warehouseId;
        this.inventoryId = inventoryId;
        this.quantity = quantity;
    }

    /**
     * Get the full location string (e.g., "A-3-B" for Aisle A, Rack 3, Shelf B).
     */
    public String getFullLocation() {
        StringBuilder location = new StringBuilder();
        if (aisle != null) {
            location.append(aisle);
        }
        if (rack != null) {
            if (location.length() > 0) location.append("-");
            location.append(rack);
        }
        if (shelf != null) {
            if (location.length() > 0) location.append("-");
            location.append(shelf);
        }
        return location.toString();
    }
}
