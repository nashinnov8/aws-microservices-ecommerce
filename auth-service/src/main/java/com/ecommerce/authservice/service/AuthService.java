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
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;


@Service
@Slf4j
public class AuthService {
    private final UserCredentialRepository repository;
    private final JwtService jwtService;
    private final PasswordEncoder encoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final EmailService emailService;
    private final String baseUrl = "http://localhost:8080";
    public AuthService(UserCredentialRepository repository, JwtService jwtService, PasswordEncoder encoder, RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties, EmailService service) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.encoder = encoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.emailService = service;
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

        // Send email verification logic can be added here
        String verificationToken = jwtService.generateVerificationToken(userSaved.getId().toString());
        String verificationLink = baseUrl + "/auth/verify-email?token=" + verificationToken;
        log.info("Verification link (send this via email): {}", verificationLink);

        emailService.sendMail(userSaved.getEmail(), "Email Verification",
                "Please verify your email by clicking the following link: " + verificationLink);

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

    public void logout(LogoutRequest request) {
        String refreshToken = request.refreshToken();
        String refreshTokenHash = DigestUtils.sha256Hex(refreshToken);

        // Find the refresh token in the database
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Revoke the token by setting the revokedAt field
        tokenEntity.setRevokedAt(Instant.now());
        refreshTokenRepository.save(tokenEntity);

        // Optionally, you can log this event for security audits
        log.info("Refresh token revoked for user ID: {}", tokenEntity.getUser().getId());
    }

    public void verifyEmail(EmailVerificationRequest request) {
        String token = request.token();

        // Validate and extract user ID from token
        Claims claims = jwtService.getClaims(token);
        String userId = claims.getSubject();

        UserCredential user = repository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new UserNotExistException("User not found"));

        if (user.isEnabled()) {
            log.info("Email already verified for user: {}", user.getUsername());
            return;
        }

        // Enable the user account
        user.setEnabled(true);
        repository.save(user);

        log.info("Email verified successfully for user: {}", user.getUsername());
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email();

        UserCredential user = repository.findByEmail(email)
                .orElseThrow(() -> new UserNotExistException("User with this email not found"));

        // Generate password reset token
        String resetToken = jwtService.generatePasswordResetToken(user.getId().toString());

        // Save hashed token and expiry to user
        user.setPasswordResetToken(DigestUtils.sha256Hex(resetToken));
        user.setPasswordResetTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        repository.save(user);

        // Send reset password email
        String resetLink = baseUrl + "/auth/reset-password?token=" + resetToken;
        emailService.sendMail(user.getEmail(), "Password Reset Request",
                "Click the following link to reset your password: " + resetLink + "\n\nThis link will expire in 1 hour.");

        log.info("Password reset email sent to: {}", email);
    }

    public void resetPassword(ResetPasswordRequest request) {
        String token = request.token();
        String hashedToken = DigestUtils.sha256Hex(token);

        // Find user by reset token
        UserCredential user = repository.findByPasswordResetToken(hashedToken)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        // Check if token is expired
        if (user.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw new RuntimeException("Password reset token has expired");
        }

        // Update password
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        repository.save(user);

        log.info("Password reset successful for user: {}", user.getUsername());
    }
}
