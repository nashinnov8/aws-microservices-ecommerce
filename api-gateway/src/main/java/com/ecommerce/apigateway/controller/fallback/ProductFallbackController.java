package com.ecommerce.apigateway.controller.fallback;

import com.ecommerce.apigateway.utils.FallbackResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Fallback controller for Product Service.
 * Handles circuit breaker fallbacks when product-service is unavailable.
 */
@RestController
@RequestMapping("/fallback/products")
@Slf4j
public class ProductFallbackController {

    private static final String SERVICE_NAME = "Product service";

    @GetMapping
    public ResponseEntity<Map<String, Object>> fallbackGet() {
        log.warn("{} fallback triggered for GET request", SERVICE_NAME);
        return FallbackResponseUtil.createServiceUnavailableResponse(SERVICE_NAME);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> fallbackPost() {
        log.warn("{} fallback triggered for POST request", SERVICE_NAME);
        return FallbackResponseUtil.createServiceUnavailableResponse(SERVICE_NAME);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> fallbackPut() {
        log.warn("{} fallback triggered for PUT request", SERVICE_NAME);
        return FallbackResponseUtil.createServiceUnavailableResponse(SERVICE_NAME);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> fallbackDelete() {
        log.warn("{} fallback triggered for DELETE request", SERVICE_NAME);
        return FallbackResponseUtil.createServiceUnavailableResponse(SERVICE_NAME);
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<Map<String, Object>> fallbackAll() {
        log.warn("{} fallback triggered for wildcard request", SERVICE_NAME);
        return FallbackResponseUtil.createServiceUnavailableResponse(SERVICE_NAME);
    }
}
