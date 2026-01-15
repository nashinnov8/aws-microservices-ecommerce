package com.ecommerce.authservice.dto;

public record UserInfo(
        String id,
        String email,
        String fullName,
        String role
) {}
