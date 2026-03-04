package com.ecommerce.orderservice.dto.order;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for a single order item.
 */
public record OrderItemResponse(
        UUID id,
        String sku,
        UUID productId,
        UUID variantId,
        String productName,
        String variantName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}