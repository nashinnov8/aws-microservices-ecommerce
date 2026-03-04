package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.repository.OrderRepository;
import com.ecommerce.orderservice.dto.order.CreateOrderRequest;
import com.ecommerce.orderservice.dto.order.OrderItemResponse;
import com.ecommerce.orderservice.dto.order.OrderResponse;
import com.ecommerce.orderservice.dto.order.OrderSummaryResponse;
import com.ecommerce.orderservice.kafka.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing orders, including creation, retrieval, and business logic related to order processing.
 * <p>
 * Order Flow:
 * <p>
 * 1. Create order -> validate -> reserve stock (REST) -> save as STOCK_RESERVED
 * <p>
 * 2. Confirm order (after payment) -> publish ORDER_COMPLETED -> set CONFIRMED
 * <p>
 * 3. Update status -> PROCESSING -> SHIPPED -> DELIVERED
 * <p>
 * 4. Cancel order -> publish ORDER_CANCELLED -> set CANCELLED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Create a new order and trigger the saga.
     * <p>
     * Steps:
     * 1. Generate order number
     * 2. Build Order entity with items
     * 3. Save order as PENDING
     * 4. Publish ORDER_CREATED to Kafka → inventory-service will attempt reservation
     * <p>
     * The order stays in PENDING until inventory-service responds via Kafka:
     * - STOCK_RESERVED → InventoryEventListener updates to STOCK_RESERVED
     * - STOCK_RESERVATION_FAILED → InventoryEventListener updates to FAILED
     * <p>
     * Client should poll GET /api/orders/{id} to check status.
     *
     * @param userId User ID from X-User-Id header
     * @param request CreateOrderRequest with items and shipping info
     * @return OrderResponse with status PENDING
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String userId) {
        log.info("Creating order for user: {}, request: {}", userId, request);

        // 1. Generate order number
        String orderNumber = generateOrderNumber();

        // 2. Create order entity
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setNotes(request.notes());
        order.setUserId(userId);
        order.setShippingAddressLine1(request.shippingAddressLine1());
        order.setShippingAddressLine2(request.shippingAddressLine2());
        order.setShippingCity(request.shippingCity());
        order.setShippingState(request.shippingState());
        order.setShippingZipCode(request.shippingZipCode());
        order.setShippingCountry(request.shippingCountry());
        order.setCurrency(request.currency() != null ? request.currency() : "USD");

        // 3. Add items to order
        request.items().forEach(item -> {
            OrderItem orderItem = new OrderItem(
                    item.sku(),
                    item.productId(),
                    item.variantId(),
                    item.productName(),
                    item.variantName(),
                    item.quantity(),
                    item.unitPrice()
            );
            order.addItem(orderItem);
        });
        order.recalculateTotalAmount();

        // 4. Save order (initially PENDING)
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved as PENDING: {}, ID: {}", orderNumber, savedOrder.getId());

        // 5. Publish ORDER_CREATED → inventory-service will reserve stock asynchronously
        orderEventProducer.publishOrderCreated(savedOrder);

        return mapToOrderResponse(savedOrder);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate a unique human-readable order number.
     * Format: ORD-YYYYMMDD-XXXX (e.g., ORD-20260302-A3F7)
     */
    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04X", random.nextInt(0xFFFF));
        String orderNumber = "ORD-" + datePart + "-" + randomPart;

        // Ensure uniqueness (extremely rare collision)
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            randomPart = String.format("%04X", random.nextInt(0xFFFF));
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
