package com.ecommerce.orderservice.dto.order;

import com.ecommerce.orderservice.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight order summary for paginated lists (no items included).
 */
public record OrderSummaryResponse(
        UUID id,
        String orderNumber,
        String userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {}

