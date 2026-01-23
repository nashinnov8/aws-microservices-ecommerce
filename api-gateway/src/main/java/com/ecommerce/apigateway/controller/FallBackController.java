package com.ecommerce.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallBackController {
    @GetMapping("/auth")
    public ResponseEntity<?> authServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "message", "Auth service is temporarily unavailable. Please try again later."
                ));
    }

    @GetMapping("/products")
    public ResponseEntity<?> productServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "message", "Product service is temporarily unavailable. Please try again later."
                ));
    }

    @GetMapping("/inventory")
    public ResponseEntity<?> inventoryServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "message", "Inventory service is temporarily unavailable. Please try again later."
                ));
    }
}
