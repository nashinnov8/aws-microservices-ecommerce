package com.ecommerce.inventoryservice.kafka;

import com.ecommerce.inventoryservice.dto.event.OrderEvent;
import com.ecommerce.inventoryservice.dto.reservation.ReservationResponse;
import com.ecommerce.inventoryservice.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes order events from order-service.
 * Manages stock reservations based on order lifecycle events.
 *
 * Events handled:
 * - ORDER_CANCELLED → Release all reservations for the order
 * - ORDER_COMPLETED → Fulfill all reservations for the order
 * - ORDER_PAYMENT_FAILED → Release all reservations for the order
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "order-events",
            groupId = "inventory-service",
            containerFactory = "orderEventListenerContainerFactory"
    )
    public void handleOrderEvent(String message) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            log.info("Received order event: {} for order: {}", event.eventType(), event.orderId());

            switch (event.eventType()) {
                case OrderEvent.ORDER_COMPLETED -> handleOrderCompleted(event);
                case OrderEvent.ORDER_CANCELLED -> handleOrderCancelled(event);
                case OrderEvent.ORDER_PAYMENT_FAILED -> handleOrderPaymentFailed(event);
                default -> log.warn("Unknown order event type: {}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Error processing order event message: {}", message, e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }

    /**
     * Handle order completed — fulfill all reservations.
     * Stock moves from reserved to "shipped" (leaves the system).
     */
    private void handleOrderCompleted(OrderEvent event) {
        log.info("Order completed: {}. Fulfilling reservations...", event.orderId());

        List<ReservationResponse> reservations = reservationService.getOrderReservationResponses(event.orderId());
        for (ReservationResponse reservation : reservations) {
            try {
                reservationService.fulfillReservation(reservation.id());
                log.info("Fulfilled reservation: {} for order: {}", reservation.id(), event.orderId());
            } catch (Exception e) {
                log.error("Failed to fulfill reservation: {} for order: {}",
                        reservation.id(), event.orderId(), e);
            }
        }
    }

    /**
     * Handle order cancelled — release all reservations and return stock.
     */
    private void handleOrderCancelled(OrderEvent event) {
        log.info("Order cancelled: {}. Releasing reservations...", event.orderId());

        List<ReservationResponse> reservations = reservationService.getOrderReservationResponses(event.orderId());
        for (ReservationResponse reservation : reservations) {
            try {
                reservationService.releaseReservation(reservation.id(), "ORDER_CANCELLED");
                log.info("Released reservation: {} for order: {}", reservation.id(), event.orderId());
            } catch (Exception e) {
                log.error("Failed to release reservation: {} for order: {}",
                        reservation.id(), event.orderId(), e);
            }
        }
    }

    /**
     * Handle payment failed — release all reservations and return stock.
     */
    private void handleOrderPaymentFailed(OrderEvent event) {
        log.info("Payment failed for order: {}. Releasing reservations...", event.orderId());

        List<ReservationResponse> reservations = reservationService.getOrderReservationResponses(event.orderId());
        for (ReservationResponse reservation : reservations) {
            try {
                reservationService.releaseReservation(reservation.id(), "PAYMENT_FAILED");
                log.info("Released reservation: {} for order: {}", reservation.id(), event.orderId());
            } catch (Exception e) {
                log.error("Failed to release reservation: {} for order: {}",
                        reservation.id(), event.orderId(), e);
            }
        }
    }
}

