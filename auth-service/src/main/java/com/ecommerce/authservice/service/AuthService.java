package com.ecommerce.authservice.service;

import com.ecommerce.authservice.config.JwtProperties;
import com.ecommerce.authservice.domain.entity.RefreshToken;
import com.ecommerce.authservice.domain.entity.UserCredential;
import com.ecommerce.authservice.domain.enums.Role;
import com.ecommerce.authservice.domain.repository.RefreshTokenRepository;
import com.ecommerce.authservice.domain.repository.UserCredentialRepository;
import com.ecommerce.authservice.dto.*;
import com.ecommerce.authservice.exception.UserAlreadyExistsException;
import com.ecommerce.authservice.exception.UserNotExistException;
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
                .orElseThrow(() -> new UserNotExistException("User not found"));

        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new UserNotExistException("Invalid credentials");
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

    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        String hashedRefreshToken = DigestUtils.sha256Hex(refreshToken);

        var refreshTokenEntity = refreshTokenRepository.findByTokenHash(hashedRefreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Check if the refreshToken is expired
        if (refreshTokenEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        // Get user credential information for response
        UserCredential user = refreshTokenEntity.getUser();

        // Generate new access token and refresh token
        String newAccessToken = jwtService.generateAccessToken(user.getId().toString(), user.getRole());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId().toString());

        // Create new refresh token entity and store back to the database
        RefreshToken newRefreshTokenEntity = new RefreshToken();
        newRefreshTokenEntity.setTokenHash(DigestUtils.sha256Hex(newRefreshToken));
        newRefreshTokenEntity.setUser(user);
        newRefreshTokenEntity.setExpiresAt(Instant.now().plus(jwtProperties.getExpirationRefresh(), ChronoUnit.MILLIS));
        newRefreshTokenEntity.setIpAddress(refreshTokenEntity.getIpAddress());
        newRefreshTokenEntity.setDeviceInfo(refreshTokenEntity.getDeviceInfo());
        refreshTokenRepository.save(newRefreshTokenEntity);

        // Revoke the old refresh token
        refreshTokenEntity.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshTokenEntity);


        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
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
