package com.ecommerce.inventoryservice.domain.entity;

import com.ecommerce.inventoryservice.domain.enums.MovementType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for maintaining an audit trail of all stock movements.
 * Records every change to inventory with full context for traceability.
 */
@Entity
@Table(name = "stock_movements", indexes = {
    @Index(name = "idx_sm_inventory_id", columnList = "inventoryId"),
    @Index(name = "idx_sm_warehouse_id", columnList = "warehouseId"),
    @Index(name = "idx_sm_movement_type", columnList = "movementType"),
    @Index(name = "idx_sm_performed_at", columnList = "performedAt"),
    @Index(name = "idx_sm_reference_id", columnList = "referenceId")
})
@Getter
@Setter
@NoArgsConstructor
public class StockMovement extends BaseEntity {

    /**
     * Reference to the inventory item.
     */
    @NotNull(message = "Inventory ID is required")
    @Column(nullable = false)
    private UUID inventoryId;

    /**
     * Reference to the warehouse (optional, for warehouse-specific movements).
     */
    private UUID warehouseId;

    /**
     * Type of movement (RECEIVE, SHIP, ADJUST, etc.).
     */
    @NotNull(message = "Movement type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType movementType;

    /**
     * Quantity moved (positive for additions, can be positive or negative).
     */
    @Column(nullable = false)
    private int quantity;

    /**
     * Stock quantity before this movement.
     */
    @Column(nullable = false)
    private int previousQuantity;

    /**
     * Stock quantity after this movement.
     */
    @Column(nullable = false)
    private int newQuantity;

    /**
     * Reason or description for the movement.
     */
    @Column(length = 500)
    private String reason;

    /**
     * External reference ID (e.g., order ID, transfer ID, PO number).
     */
    @Column(length = 100)
    private String referenceId;

    /**
     * User or system that performed this movement.
     */
    @Column(length = 100)
    private String performedBy;

    /**
     * Timestamp when the movement was performed.
     */
    @NotNull(message = "Performed at timestamp is required")
    @Column(nullable = false)
    private Instant performedAt;

    /**
     * Constructor for creating a stock movement record.
     */
    public StockMovement(UUID inventoryId, MovementType movementType, int quantity,
                         int previousQuantity, int newQuantity, String reason, String performedBy) {
        this.inventoryId = inventoryId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.reason = reason;
        this.performedBy = performedBy;
        this.performedAt = Instant.now();
    }

    /**
     * Builder-style method to set warehouse ID.
     */
    public StockMovement withWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
        return this;
    }

    /**
     * Builder-style method to set reference ID.
     */
    public StockMovement withReferenceId(String referenceId) {
        this.referenceId = referenceId;
        return this;
    }
}

