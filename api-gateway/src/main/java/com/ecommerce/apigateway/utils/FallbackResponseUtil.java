package com.ecommerce.apigateway.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for creating standardized fallback responses
 * when downstream services are unavailable.
 */
public final class FallbackResponseUtil {

    private FallbackResponseUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a standardized service unavailable response.
     *
     * @param serviceName the name of the unavailable service
     * @return ResponseEntity with 503 status and error details
     */
    public static ResponseEntity<Map<String, Object>> createServiceUnavailableResponse(String serviceName) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", serviceName + " is temporarily unavailable. Please try again later.");
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Creates a standardized error response with custom message.
     *
     * @param status  the HTTP status
     * @param message the error message
     * @return ResponseEntity with specified status and error details
     */
    public static ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }
}
