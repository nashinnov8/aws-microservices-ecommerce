package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckRequest;
import com.ecommerce.inventoryservice.dto.inventory.BulkStockCheckResponse;
import com.ecommerce.inventoryservice.dto.reservation.*;
import com.ecommerce.inventoryservice.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for stock reservation operations.
 *
 * This is a thin controller that only handles HTTP concerns (request/response).
 * All business logic is delegated to ReservationService.
 *
 * Endpoints:
 * - POST /api/reservations/reserve - Reserve stock for a single item
 * - POST /api/reservations/bulk-reserve - Reserve stock for multiple items
 * - POST /api/reservations/check-availability - Check availability without reserving
 * - POST /api/reservations/{id}/confirm - Confirm a pending reservation
 * - POST /api/reservations/{id}/release - Release a reservation
 * - GET /api/reservations/order/{orderId} - Get all reservations for an order
 * - GET /api/reservations/{id} - Get details of a specific reservation
 * - GET /api/reservations/admin/active-reservations - Get all active reservations
 * - GET /api/reservations/admin/expired-reservations - Get all expired reservations
 * - POST /api/reservations/admin/cleanup-expired - Clean up expired reservations
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Slf4j
public class ReservationController {
    private final ReservationService reservationService;

    /**
     * Reserve stock for a single item.
     *
     * @param request Reserve stock request containing SKU, order ID, and quantity
     * @return Reservation response with reservation ID and status
     */
    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
            @Valid @RequestBody ReserveStockRequest request) {
        log.info("POST /api/reservations/reserve - Order: {}, SKU: {}, Quantity: {}",
                request.orderId(), request.sku(), request.quantity());

        ReservationResponse response = reservationService.reserveSingleStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("201", "Stock reserved successfully", response)
        );
    }

    /**
     * Reserve stock for multiple items in a single atomic operation.
     *
     * @param request Bulk reserve request with order ID and list of items
     * @return Bulk reservation response with all reservation details
     */
    @PostMapping("/bulk-reserve")
    public ResponseEntity<ApiResponse<BulkReservationResponse>> bulkReserveStock(
            @Valid @RequestBody BulkReserveStockRequest request) {
        log.info("POST /api/reservations/bulk-reserve - Order: {}, Items: {}",
                request.orderId(), request.items().size());

        BulkReservationResponse response = reservationService.reserveBulkStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("201", "Bulk reservation processed", response)
        );
    }

    /**
     * Check stock availability without creating reservations.
     *
     * @param request Bulk stock check request
     * @return Stock availability check results for each item
     */
    @PostMapping("/check-availability")
    public ResponseEntity<ApiResponse<BulkStockCheckResponse>> checkAvailability(
            @Valid @RequestBody BulkStockCheckRequest request) {
        log.info("POST /api/reservations/check-availability - Items: {}", request.items().size());

        BulkStockCheckResponse response = reservationService.checkAvailability(request);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Availability check completed", response)
        );
    }

    /**
     * Confirm a pending reservation (called after payment success).
     *
     * @param reservationId UUID of the reservation to confirm
     * @return Confirmed reservation details
     */
    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ApiResponse<ReservationResponse>> confirmReservation(
            @PathVariable UUID reservationId) {
        log.info("POST /api/reservations/{}/confirm", reservationId);

        ReservationResponse response = reservationService.confirmReservation(reservationId);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservation confirmed successfully", response)
        );
    }

    /**
     * Release a reservation (called on payment failure or order cancellation).
     *
     * @param reservationId UUID of the reservation to release
     * @param request Update request containing the action and optional reason
     * @return Released reservation details
     */
    @PostMapping("/{reservationId}/release")
    public ResponseEntity<ApiResponse<ReservationResponse>> releaseReservation(
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationRequest request) {
        log.info("POST /api/reservations/{}/release - Reason: {}", reservationId, request.reason());

        ReservationResponse response = reservationService.releaseReservation(reservationId, request.reason());
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservation released successfully", response)
        );
    }

    /**
     * Get all reservations for a specific order.
     *
     * @param orderId Order ID
     * @return List of reservations for the order
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getOrderReservations(
            @PathVariable String orderId) {
        log.info("GET /api/reservations/order/{}", orderId);

        List<ReservationResponse> responses = reservationService.getOrderReservationResponses(orderId);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservations retrieved successfully", responses)
        );
    }

    /**
     * Get details of a specific reservation by ID.
     *
     * @param reservationId Reservation UUID
     * @return Reservation details
     */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(
            @PathVariable UUID reservationId) {
        log.info("GET /api/reservations/{}", reservationId);

        ReservationResponse response = reservationService.getReservationResponse(reservationId);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Reservation retrieved successfully", response)
        );
    }

    /**
     * Get all active reservations (admin endpoint).
     *
     * @return List of active reservations
     */
    @GetMapping("/admin/active-reservations")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getActiveReservations() {
        log.info("GET /api/reservations/admin/active-reservations");

        List<ReservationResponse> responses = reservationService.getActiveReservationResponses();
        return ResponseEntity.ok(
                ApiResponse.success("200", "Active reservations retrieved successfully", responses)
        );
    }

    /**
     * Get expired reservations (admin endpoint).
     *
     * @return List of expired reservations
     */
    @GetMapping("/admin/expired-reservations")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getExpiredReservations() {
        log.info("GET /api/reservations/admin/expired-reservations");

        List<ReservationResponse> responses = reservationService.getExpiredReservationResponses();
        return ResponseEntity.ok(
                ApiResponse.success("200", "Expired reservations retrieved successfully", responses)
        );
    }

    /**
     * Clean up expired reservations (admin endpoint).
     *
     * @return Number of reservations cleaned up
     */
    @PostMapping("/admin/cleanup-expired")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Integer>> cleanupExpiredReservations() {
        log.info("POST /api/reservations/admin/cleanup-expired");

        int cleanedCount = reservationService.cleanupExpiredReservations();
        return ResponseEntity.ok(
                ApiResponse.success("200", "Expired reservations cleaned up", cleanedCount)
        );
    }
}


