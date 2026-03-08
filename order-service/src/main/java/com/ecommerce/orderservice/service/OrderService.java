package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.domain.repository.OrderRepository;
import com.ecommerce.orderservice.dto.order.*;
import com.ecommerce.orderservice.exception.InvalidOrderStateException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.kafka.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing orders using the Kafka choreography saga pattern.
 *
 * Saga Flow:
 * 1. createOrder → save as PENDING → publish ORDER_CREATED to Kafka
 * 2. InventoryEventListener receives STOCK_RESERVED → update to STOCK_RESERVED
 *    OR receives STOCK_RESERVATION_FAILED → update to FAILED
 * 3. confirmOrder → publish ORDER_COMPLETED → update to CONFIRMED
 * 4. cancelOrder → publish ORDER_CANCELLED → update to CANCELLED
 *
 * NO synchronous REST calls — fully event-driven.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== CREATE ORDER (SAGA TRIGGER) ====================

    /**
     * Create a new order and trigger the saga.
     *
     * Steps:
     * 1. Generate order number
     * 2. Build Order entity with items
     * 3. Save order as PENDING
     * 4. Publish ORDER_CREATED to Kafka → inventory-service will attempt reservation
     *
     * The order stays in PENDING until inventory-service responds via Kafka:
     * - STOCK_RESERVED → InventoryEventListener updates to STOCK_RESERVED
     * - STOCK_RESERVATION_FAILED → InventoryEventListener updates to FAILED
     *
     * Client should poll GET /api/orders/{id} to check status.
     *
     * @param userId  User ID from X-User-Id header
     * @param request CreateOrderRequest with items and shipping info
     * @return OrderResponse with status PENDING
     */
    @Transactional
    public OrderResponse createOrder(String userId, CreateOrderRequest request) {
        log.info("Creating order for user: {} with {} items", userId, request.items().size());

        // 1. Generate unique order number
        String orderNumber = generateOrderNumber();

        // 2. Build order entity
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(request.currency() != null ? request.currency() : "USD");
        order.setShippingAddressLine1(request.shippingAddressLine1());
        order.setShippingAddressLine2(request.shippingAddressLine2());
        order.setShippingCity(request.shippingCity());
        order.setShippingState(request.shippingState());
        order.setShippingZipCode(request.shippingZipCode());
        order.setShippingCountry(request.shippingCountry());
        order.setNotes(request.notes());

        // 3. Add items
        for (OrderItemRequest itemReq : request.items()) {
            OrderItem item = new OrderItem(
                    itemReq.sku(),
                    itemReq.productId(),
                    itemReq.variantId(),
                    itemReq.productName(),
                    itemReq.variantName(),
                    itemReq.quantity(),
                    itemReq.unitPrice()
            );
            order.addItem(item);
        }
        order.recalculateTotalAmount();

        // 4. Save order as PENDING
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved as PENDING: {}, ID: {}", orderNumber, savedOrder.getId());

        // 5. Publish ORDER_CREATED → inventory-service will reserve stock asynchronously
        orderEventProducer.publishOrderCreated(savedOrder);

        return mapToOrderResponse(savedOrder);
    }

    // ==================== GET ORDERS ====================

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> OrderNotFoundException.byOrderNumber(orderNumber));
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByUserId(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToOrderSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByUserIdAndStatus(
            String userId, OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable)
                .map(this::mapToOrderSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByStatus(OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::mapToOrderSummaryResponse);
    }

    // ==================== CONFIRM ORDER (SAGA SUCCESS) ====================

    /**
     * Confirm an order after payment success.
     * Publishes ORDER_COMPLETED → inventory-service fulfills reservations.
     *
     * Pre-condition: Order must be in STOCK_RESERVED status.
     */
    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            throw new InvalidOrderStateException(order.getStatus(), "confirm");
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);

        // Publish ORDER_COMPLETED → inventory-service fulfills reservations
        orderEventProducer.publishOrderCompleted(saved);

        log.info("Order confirmed: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    // ==================== STATUS TRANSITIONS (ADMIN) ====================

    @Transactional
    public OrderResponse markAsProcessing(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(order.getStatus(), "mark as processing");
        }

        order.setStatus(OrderStatus.PROCESSING);
        Order saved = orderRepository.save(order);
        log.info("Order marked as processing: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    @Transactional
    public OrderResponse markAsShipped(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidOrderStateException(order.getStatus(), "mark as shipped");
        }

        order.setStatus(OrderStatus.SHIPPED);
        Order saved = orderRepository.save(order);
        log.info("Order shipped: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    @Transactional
    public OrderResponse markAsDelivered(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new InvalidOrderStateException(order.getStatus(), "mark as delivered");
        }

        order.setStatus(OrderStatus.DELIVERED);
        Order saved = orderRepository.save(order);
        log.info("Order delivered: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    // ==================== CANCEL ORDER (SAGA COMPENSATION) ====================

    /**
     * Cancel an order. If stock was reserved, publishes ORDER_CANCELLED
     * so inventory-service releases the reservations (compensation).
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (!order.getStatus().isCancellable()) {
            throw new InvalidOrderStateException(order.getStatus(), "cancel");
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledReason(reason);
        Order saved = orderRepository.save(order);

        // Publish compensation event only if stock was already reserved
        if (previousStatus == OrderStatus.STOCK_RESERVED
                || previousStatus == OrderStatus.CONFIRMED
                || previousStatus == OrderStatus.PROCESSING) {
            orderEventProducer.publishOrderCancelled(saved);
            log.info("Published ORDER_CANCELLED for order: {} (compensation)", saved.getOrderNumber());
        }

        log.info("Order cancelled: {} (was: {}), reason: {}",
                saved.getOrderNumber(), previousStatus, reason);
        return mapToOrderResponse(saved);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate a unique human-readable order number.
     * Format: ORD-YYYYMMDD-XXXX (e.g., ORD-20260302-A3F7)
     */
    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04X", RANDOM.nextInt(0xFFFF));
        String orderNumber = "ORD-" + datePart + "-" + randomPart;

        // Ensure uniqueness (extremely rare collision)
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            randomPart = String.format("%04X", RANDOM.nextInt(0xFFFF));
            orderNumber = "ORD-" + datePart + "-" + randomPart;
        }

        return orderNumber;
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getSku(),
                        item.getProductId(),
                        item.getVariantId(),
                        item.getProductName(),
                        item.getVariantName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getShippingAddressLine1(),
                order.getShippingAddressLine2(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingZipCode(),
                order.getShippingCountry(),
                order.getNotes(),
                order.getCancelledReason(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderSummaryResponse mapToOrderSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getItems().size(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
