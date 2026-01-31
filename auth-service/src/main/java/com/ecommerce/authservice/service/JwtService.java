package com.ecommerce.authservice.service;

import com.ecommerce.authservice.config.JwtProperties;
import com.ecommerce.authservice.constants.JwtConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
@Slf4j
public class JwtService {
    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }

    public String generateAccessToken(String userId, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim(JwtConstants.CLAIM_ROLE, role)
                .claim(JwtConstants.CLAIM_TYPE, JwtConstants.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim(JwtConstants.CLAIM_TYPE, JwtConstants.TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationRefresh()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateVerificationToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim(JwtConstants.CLAIM_PURPOSE, JwtConstants.TOKEN_PURPOSE_EMAIL_VERIFICATION)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getEmailVerificationExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generatePasswordResetToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim(JwtConstants.CLAIM_PURPOSE, JwtConstants.TOKEN_PURPOSE_PASSWORD_RESET)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getPasswordResetExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String validateVerificationToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String purpose = claims.get(JwtConstants.CLAIM_PURPOSE, String.class);
            log.info("Token purpose: {}", purpose);
            if (!JwtConstants.TOKEN_PURPOSE_EMAIL_VERIFICATION.equals(purpose)) {
                throw new RuntimeException(JwtConstants.INVALID_TOKEN_PURPOSE);
            }

            return claims.getSubject();
        } catch (Exception e) {
            throw new RuntimeException(JwtConstants.INVALID_OR_EXPIRED_VERIFICATION_TOKEN, e);
        }
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public Claims getClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());

        // Parse the token and validate its signature
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token, String expectedType) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return expectedType.equals(claims.get(JwtConstants.CLAIM_TYPE));
        } catch (Exception e) {
            return false;
        }
    }
}
