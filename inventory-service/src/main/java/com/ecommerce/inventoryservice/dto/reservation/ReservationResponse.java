package com.ecommerce.inventoryservice.dto.reservation;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for reservation operations.
 *
 * @param id Reservation UUID
 * @param sku SKU of the reserved item
 * @param orderId Order this reservation is for
 * @param quantity Quantity reserved
 * @param status Current status (ACTIVE, FULFILLED, CANCELLED, EXPIRED)
 * @param expiresAt When this reservation expires
 * @param createdAt When the reservation was created
 * @param fulfilledAt When fulfilled (null if not yet fulfilled)
 * @param cancelledAt When cancelled (null if not cancelled)
 */
public record ReservationResponse(
        UUID id,
        String sku,
        String orderId,
        int quantity,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant fulfilledAt,
        Instant cancelledAt
) {}
