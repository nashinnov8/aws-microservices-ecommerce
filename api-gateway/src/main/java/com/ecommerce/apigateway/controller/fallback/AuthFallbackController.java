package com.ecommerce.apigateway.controller.fallback;

import com.ecommerce.apigateway.service.TokenBlacklistService;
import com.ecommerce.apigateway.utils.FallbackResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Fallback controller for Auth Service.
 * Handles circuit breaker fallbacks when auth-service is unavailable.
 */
@RestController
@RequestMapping("/fallback/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthFallbackController {

    private static final String SERVICE_NAME = "Auth service";

    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("message", "Missing or invalid authorization header");
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        }

        String token = authHeader.substring(7);

        return tokenBlacklistService.blacklistToken(token, 3600000)
                .map(success -> {
                    Map<String, Object> body = new HashMap<>();
                    if (success) {
                        log.info("User logged out successfully, token blacklisted");
                        body.put("success", true);
                        body.put("message", "Logout successful, token has been revoked");
                        return ResponseEntity.ok(body);
                    } else {
                        log.error("Failed to blacklist token during logout");
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

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<Map<String, Object>> fallbackAll() {
        log.warn("{} fallback triggered for wildcard request", SERVICE_NAME);
        return FallbackResponseUtil.createServiceUnavailableResponse(SERVICE_NAME);
    }
}
