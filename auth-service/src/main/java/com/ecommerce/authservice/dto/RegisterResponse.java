package com.ecommerce.authservice.dto;

public record RegisterResponse(String userId, String email, String username, String role) {
}
