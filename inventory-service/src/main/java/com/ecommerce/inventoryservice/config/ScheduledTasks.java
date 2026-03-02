package com.ecommerce.inventoryservice.config;

import com.ecommerce.inventoryservice.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for inventory management.
 *
 * Tasks:
 * - Cleanup expired reservations (every 5 minutes)
 * - Could be extended for other periodic tasks
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final ReservationService reservationService;

    /**
     * Cleanup expired reservations.
     *
     * Runs every 5 minutes (300,000 milliseconds).
     * Releases any reservations that have expired and restores stock.
     *
     * This is important for preventing stale reservations from
     * blocking stock availability indefinitely.
     *
     * Schedule can be configured via application properties:
     * app.scheduler.reservation-cleanup-interval=300000
     */
    @Scheduled(fixedDelay = 300000)  // 5 minutes
    public void cleanupExpiredReservations() {
        log.info("Starting scheduled cleanup of expired reservations");

        try {
            int cleanedCount = reservationService.cleanupExpiredReservations();
            log.info("Cleanup completed. Removed {} expired reservations", cleanedCount);
        } catch (Exception e) {
            log.error("Error during expired reservation cleanup", e);
            // Don't rethrow - we want the scheduler to continue
        }
    }

    /**
     * Optional: Log reservation statistics periodically.
     * Helps monitor reservation health.
     *
     * Uncomment to enable (runs every 1 hour).
     */
    // @Scheduled(fixedDelay = 3600000)  // 1 hour
    // public void logReservationStatistics() {
    //     log.info("Reservation statistics: Active={}, Expired={}",
    //             reservationService.getActiveReservations().size(),
    //             reservationService.getExpiredReservations().size());
    // }
}

