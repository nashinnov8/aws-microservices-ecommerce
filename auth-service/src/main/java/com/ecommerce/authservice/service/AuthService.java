package com.ecommerce.authservice.service;

import com.ecommerce.authservice.config.JwtProperties;
import com.ecommerce.authservice.domain.entity.RefreshToken;
import com.ecommerce.authservice.domain.entity.UserCredential;
import com.ecommerce.authservice.domain.enums.Role;
import com.ecommerce.authservice.domain.repository.RefreshTokenRepository;
import com.ecommerce.authservice.domain.repository.UserCredentialRepository;
import com.ecommerce.authservice.dto.*;
import com.ecommerce.authservice.exception.UserAlreadyExistsException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;


@Service
public class AuthService {
    private final UserCredentialRepository repository;
    private final JwtService jwtService;
    private final PasswordEncoder encoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public AuthService(UserCredentialRepository repository, JwtService jwtService, PasswordEncoder encoder, RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.encoder = encoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    public RegisterResponse register(RegisterRequest request) {
        // Find if user already exists
        if (repository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("User already taken");
        }

        // Registration logic here
        UserCredential userCredential = new UserCredential();
        userCredential.setUsername(request.username());
        userCredential.setEmail(request.email());
        userCredential.setPasswordHash(encoder.encode(request.password()));
        userCredential.setRole(Role.CUSTOMER.getAuthority()); // Default role
        var userSaved = repository.save(userCredential);

        return new RegisterResponse(
                userSaved.getId().toString(),
                userSaved.getEmail(),
                userSaved.getUsername(),
                userSaved.getRole()
        );
    }

    public LoginResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
        UserCredential user = repository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(user.getId().toString(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId().toString());


        // Save refresh token to database
        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(DigestUtils.sha256Hex(refreshToken));
        refreshTokenEntity.setExpiresAt(
                Instant.now().plus(jwtProperties.getExpirationRefresh(), ChronoUnit.MILLIS)
        );
        refreshTokenEntity.setIpAddress(ipAddress);
        refreshTokenEntity.setDeviceInfo(deviceInfo);
        refreshTokenRepository.save(refreshTokenEntity);

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProperties.getExpiration(),
                new UserInfo(
                        user.getId().toString(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRole()
                )
        );
    }
}
