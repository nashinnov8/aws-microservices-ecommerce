package com.ecommerce.inventoryservice.domain.entity;

import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for tracking stock reservations for orders.
 * When an order is placed, stock is reserved to prevent overselling.
 * Reservations can be fulfilled, cancelled, or expire.
 */
@Entity
@Table(name = "stock_reservations", indexes = {
    @Index(name = "idx_sr_inventory_id", columnList = "inventoryId"),
    @Index(name = "idx_sr_order_id", columnList = "orderId"),
    @Index(name = "idx_sr_status", columnList = "status"),
    @Index(name = "idx_sr_expires_at", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
public class  StockReservation extends BaseEntity {

    /**
     * Reference to the inventory item being reserved.
     */
    @NotNull(message = "Inventory ID is required")
    @Column(nullable = false)
    private UUID inventoryId;

    /**
     * Order ID that this reservation is for.
     */
    @NotBlank(message = "Order ID is required")
    @Column(nullable = false, length = 100)
    private String orderId;

    /**
     * Quantity reserved for this order.
     */
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private int quantity;

    /**
     * Current status of the reservation.
     */
    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    /**
     * Timestamp when this reservation expires.
     * If not fulfilled by this time, the reservation should be released.
     */
    @NotNull(message = "Expiration time is required")
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Timestamp when reservation was fulfilled (if applicable).
     */
    private Instant fulfilledAt;

    /**
     * Timestamp when reservation was cancelled (if applicable).
     */
    private Instant cancelledAt;

    /**
     * Constructor for creating a new stock reservation.
     *
     * @param inventoryId The inventory item ID
     * @param orderId The order ID
     * @param quantity The quantity to reserve
     * @param expiresAt When the reservation expires
     */
    public StockReservation(UUID inventoryId, String orderId, int quantity, Instant expiresAt) {
        this.inventoryId = inventoryId;
        this.orderId = orderId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.ACTIVE;
    }

    /**
     * Check if this reservation has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this reservation is still active.
     */
    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    /**
     * Mark this reservation as fulfilled.
     */
    public void fulfill() {
        this.status = ReservationStatus.FULFILLED;
        this.fulfilledAt = Instant.now();
    }

    /**
     * Mark this reservation as cancelled.
     */
    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    /**
     * Mark this reservation as expired.
     */
    public void expire() {
        this.status = ReservationStatus.EXPIRED;
        this.cancelledAt = Instant.now();
    }
}
