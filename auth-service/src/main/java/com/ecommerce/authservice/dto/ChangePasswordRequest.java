package com.ecommerce.authservice.dto;

public record ChangePasswordRequest(
        String userId,
        String oldPassword,
        String newPassword
) {
}
