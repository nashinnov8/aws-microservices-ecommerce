package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.order.*;
import com.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import response.ApiResponse;
import response.PageResponse;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for order operations.
 *
 * IMPORTANT: createOrder returns status PENDING (not STOCK_RESERVED).
 * Client must poll GET /api/orders/{id} to check if stock was reserved.
 *
 * Endpoints:
 * - POST   /api/orders                     - Create order (returns PENDING)
 * - GET    /api/orders/{id}                 - Get order by ID (poll for status)
 * - GET    /api/orders/number/{orderNumber} - Get order by order number
 * - GET    /api/orders/my-orders            - Get current user's orders (paginated)
 * - POST   /api/orders/{id}/confirm         - Confirm order (requires STOCK_RESERVED)
 * - POST   /api/orders/{id}/cancel          - Cancel order
 * - POST   /api/orders/{id}/processing      - Mark as processing (admin)
 * - POST   /api/orders/{id}/ship            - Mark as shipped (admin)
 * - POST   /api/orders/{id}/deliver         - Mark as delivered (admin)
 * - GET    /api/orders/admin/by-status       - Get orders by status (admin)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // ==================== CREATE ====================

    /**
     * Create a new order (async saga).
     * Returns immediately with status PENDING.
     * Client should poll GET /api/orders/{id} to check status transition:
     *   PENDING → STOCK_RESERVED (success) or PENDING → FAILED (insufficient stock)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders - User: {}, Items: {}", userId, request.items().size());

        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success("202", "Order created, awaiting stock reservation", response)
        );
    }

    // ==================== READ ====================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID id) {
        log.info("GET /api/orders/{}", id);
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order retrieved successfully", response)
        );
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByOrderNumber(
            @PathVariable String orderNumber) {
        log.info("GET /api/orders/number/{}", orderNumber);
        OrderResponse response = orderService.getOrderByOrderNumber(orderNumber);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order retrieved successfully", response)
        );
    }

    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) OrderStatus status) {
        log.info("GET /api/orders/my-orders - User: {}, Page: {}, Size: {}, Status: {}",
                userId, page, size, status);

        Page<OrderSummaryResponse> orders;
        if (status != null) {
            orders = orderService.getOrdersByUserIdAndStatus(userId, status, page, size);
        } else {
            orders = orderService.getOrdersByUserId(userId, page, size);
        }

        PageResponse<OrderSummaryResponse> pageResponse = PageResponse.from(orders);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Orders retrieved successfully", pageResponse)
        );
    }

    // ==================== STATUS TRANSITIONS ====================

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/confirm", id);
        OrderResponse response = orderService.confirmOrder(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order confirmed successfully", response)
        );
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null
                ? body.getOrDefault("reason", "Customer requested cancellation")
                : "Customer requested cancellation";
        log.info("POST /api/orders/{}/cancel - Reason: {}", id, reason);
        OrderResponse response = orderService.cancelOrder(id, reason);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order cancelled successfully", response)
        );
    }

    @PostMapping("/{id}/processing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> markAsProcessing(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/processing", id);
        OrderResponse response = orderService.markAsProcessing(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order marked as processing", response)
        );
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> markAsShipped(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/ship", id);
        OrderResponse response = orderService.markAsShipped(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order shipped successfully", response)
        );
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> markAsDelivered(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/deliver", id);
        OrderResponse response = orderService.markAsDelivered(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order delivered successfully", response)
        );
    }

    // ==================== ADMIN ====================

    @GetMapping("/admin/by-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getOrdersByStatus(
            @RequestParam OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/orders/admin/by-status - Status: {}, Page: {}, Size: {}", status, page, size);
        Page<OrderSummaryResponse> orders = orderService.getOrdersByStatus(status, page, size);
        PageResponse<OrderSummaryResponse> pageResponse = PageResponse.from(orders);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Orders retrieved successfully", pageResponse)
        );
    }
}

