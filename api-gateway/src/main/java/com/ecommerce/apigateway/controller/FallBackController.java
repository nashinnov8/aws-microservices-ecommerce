package com.ecommerce.apigateway.controller;

import com.ecommerce.apigateway.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
@RequiredArgsConstructor
@Slf4j
public class FallBackController {

    private final TokenBlacklistService tokenBlacklistService;

    // ==================== AUTH SERVICE FALLBACKS ====================

    // Specific logout endpoint - handles token blacklisting at gateway level
    @PostMapping("/auth/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        // Validate authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("message", "Missing or invalid authorization header");
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        }

        String token = authHeader.substring(7);

        // Blacklist the token (use 1 hour expiration to match JWT expiration)
        return tokenBlacklistService.blacklistToken(token, 3600000)
                .map(success -> {
                    Map<String, Object> body = new HashMap<>();
                    if (success) {
                        log.info("User logged out successfully, token blacklisted");
                        body.put("success", true);
                        body.put("message", "Logout successful, token has been revoked");
                        return ResponseEntity.ok(body);
                    } else {
                        log.error("Failed to blacklist token during logout for token: {}", token);
                        body.put("success", false);
                        body.put("message", "Logout failed, please try again");
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error during logout: {}", error.getMessage(), error);
                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put("success", false);
                    errorBody.put("message", "Logout failed: " + error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody));
                });
    }

    @GetMapping("/auth")
    public ResponseEntity<?> authServiceFallbackGet() {
        log.warn("Auth service fallback triggered for GET request");
        return createServiceUnavailableResponse("Auth service");
    }

    @PostMapping("/auth")
    public ResponseEntity<?> authServiceFallbackPost() {
        log.warn("Auth service fallback triggered for POST request");
        return createServiceUnavailableResponse("Auth service");
    }

    @PutMapping("/auth")
    public ResponseEntity<?> authServiceFallbackPut() {
        log.warn("Auth service fallback triggered for PUT request");
        return createServiceUnavailableResponse("Auth service");
    }

    @DeleteMapping("/auth")
    public ResponseEntity<?> authServiceFallbackDelete() {
        log.warn("Auth service fallback triggered for DELETE request");
        return createServiceUnavailableResponse("Auth service");
    }

    // Catch-all for any path under /auth (e.g., /fallback/auth/login, /fallback/auth/register)
    // NOTE: /auth/logout is handled by the specific endpoint above
    @RequestMapping(value = "/auth/**", method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> authServiceFallbackAll() {
        log.warn("Auth service fallback triggered for wildcard request");
        return createServiceUnavailableResponse("Auth service");
    }

    // ==================== PRODUCT SERVICE FALLBACKS ====================

    @GetMapping("/products")
    public ResponseEntity<?> productServiceFallbackGet() {
        log.warn("Product service fallback triggered for GET request");
        return createServiceUnavailableResponse("Product service");
    }

    @PostMapping("/products")
    public ResponseEntity<?> productServiceFallbackPost() {
        log.warn("Product service fallback triggered for POST request");
        return createServiceUnavailableResponse("Product service");
    }

    @RequestMapping(value = "/products/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> productServiceFallbackAll() {
        log.warn("Product service fallback triggered for wildcard request");
        return createServiceUnavailableResponse("Product service");
    }

    // Categories fallback (routed through product service)
    @RequestMapping(value = "/categories/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> categoriesServiceFallback() {
        log.warn("Categories fallback triggered (Product service)");
        return createServiceUnavailableResponse("Product service");
    }

    // Brands fallback (routed through product service)
    @RequestMapping(value = "/brands/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> brandsServiceFallback() {
        log.warn("Brands fallback triggered (Product service)");
        return createServiceUnavailableResponse("Product service");
    }

    // ==================== INVENTORY SERVICE FALLBACKS ====================

    @GetMapping("/inventory")
    public ResponseEntity<?> inventoryServiceFallbackGet() {
        log.warn("Inventory service fallback triggered for GET request");
        return createServiceUnavailableResponse("Inventory service");
    }

    @PostMapping("/inventory")
    public ResponseEntity<?> inventoryServiceFallbackPost() {
        log.warn("Inventory service fallback triggered for POST request");
        return createServiceUnavailableResponse("Inventory service");
    }

    @RequestMapping(value = "/inventory/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> inventoryServiceFallbackAll() {
        log.warn("Inventory service fallback triggered for wildcard request");
        return createServiceUnavailableResponse("Inventory service");
    }

    // ==================== HELPER METHODS ====================

    private ResponseEntity<Map<String, Object>> createServiceUnavailableResponse(String serviceName) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", serviceName + " is temporarily unavailable. Please try again later.");
        body.put("timestamp", java.time.Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
