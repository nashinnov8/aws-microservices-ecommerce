package com.ecommerce.inventoryservice.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing a physical warehouse location.
 * Used for tracking stock distribution across multiple warehouses.
 */
@Entity
@Table(name = "warehouses",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouse_code", columnNames = "warehouseCode")
    },
    indexes = {
        @Index(name = "idx_warehouse_code", columnList = "warehouseCode"),
        @Index(name = "idx_warehouse_is_active", columnList = "isActive")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Warehouse extends BaseEntity {

    /**
     * Unique warehouse code (e.g., "WH-SYD-01").
     */
    @NotBlank(message = "Warehouse code is required")
    @Column(nullable = false, unique = true, length = 50)
    private String warehouseCode;

    /**
     * Human-readable warehouse name.
     */
    @NotBlank(message = "Warehouse name is required")
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * General location description.
     */
    @Column(length = 255)
    private String location;

    /**
     * Street address of the warehouse.
     */
    @Column(length = 255)
    private String address;

    /**
     * City where the warehouse is located.
     */
    @Column(length = 100)
    private String city;

    /**
     * State/Province of the warehouse.
     */
    @Column(length = 100)
    private String state;

    /**
     * Country of the warehouse.
     */
    @Column(length = 100)
    private String country;

    /**
     * ZIP/Postal code.
     */
    @Column(length = 20)
    private String zipCode;

    /**
     * Maximum capacity of the warehouse (total units it can hold).
     */
    @Min(value = 0, message = "Capacity cannot be negative")
    @Column(nullable = false)
    private int capacity = 10000;

    /**
     * Whether this warehouse is currently active.
     */
    @Column(nullable = false)
    private boolean isActive = true;

    /**
     * Constructor for creating a new warehouse.
     */
    public Warehouse(String warehouseCode, String name) {
        this.warehouseCode = warehouseCode;
        this.name = name;
    }
}
