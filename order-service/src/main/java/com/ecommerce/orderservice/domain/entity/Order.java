package com.ecommerce.orderservice.domain.entity;

import com.ecommerce.orderservice.domain.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_number", columnNames = "orderNumber")
        },
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "userId"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_created_at", columnList = "createdAt"),
                @Index(name = "idx_orders_user_status", columnList = "userId, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    @NotBlank(message = "Order number is required")
    @Column(nullable = false, unique = true, length = 50)
    private String orderNumber;

    /**
     * User ID from auth-service (set from X-User-Id header by API gateway).
     */
    @NotBlank(message = "User ID is required")
    @Column(nullable = false, length = 100)
    private String userId;

    /**
     * Current order status.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Total amount for the order.
     * Calculated as sum of (quantity * price) for all order items.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Currency code (default USD).
     */
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    // ==================== Shipping Address ====================

    @Column(length = 255)
    private String shippingAddressLine1;

    @Column(length = 255)
    private String shippingAddressLine2;

    @Column(length = 100)
    private String shippingCity;

    @Column(length = 100)
    private String shippingState;

    @Column(length = 20)
    private String shippingZipCode;

    @Column(length = 100)
    private String shippingCountry;

    /**
     * Customer notes for the order.
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Reason for cancellation (if cancelled).
     */
    @Column(length = 500)
    private String cancelledReason;

    // ==================== Order Items ====================

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    // ==================== Helper Methods ====================
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public void recalculateTotalAmount() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
