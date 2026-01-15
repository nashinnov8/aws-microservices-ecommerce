package com.ecommerce.authservice.controller;

import com.ecommerce.authservice.dto.*;
import com.ecommerce.authservice.service.AuthService;
import com.ecommerce.authservice.utils.ClientInfoUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ApiResponse;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("200", "Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = ClientInfoUtil.getClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, clientIp, deviceInfo);
        return ResponseEntity.ok(ApiResponse.success("200", "Login successful", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("200", "Token refreshed successfully", response));
    }
}
