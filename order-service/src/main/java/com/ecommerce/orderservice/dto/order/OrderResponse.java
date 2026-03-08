package com.ecommerce.orderservice.dto.order;

import com.ecommerce.orderservice.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full order response DTO with all order details and items.
 */
public record OrderResponse(
        UUID id,
        String orderNumber,
        String userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String shippingAddressLine1,
        String shippingAddressLine2,
        String shippingCity,
        String shippingState,
        String shippingZipCode,
        String shippingCountry,
        String notes,
        String cancelledReason,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {}