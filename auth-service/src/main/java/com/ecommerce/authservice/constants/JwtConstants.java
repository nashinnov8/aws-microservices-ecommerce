package com.ecommerce.authservice.constants;

/**
 * JWT Constants for token types, purposes, and claims
 *
 * Note: Token expiration times are now configured via environment variables:
 * - JWT_EMAIL_VERIFICATION_EXPIRATION
 * - JWT_PASSWORD_RESET_EXPIRATION
 */
public class JwtConstants {

    private JwtConstants() {
        // Private constructor to prevent instantiation
    }

    // Token Types
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    // Token Purposes
    public static final String TOKEN_PURPOSE_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String TOKEN_PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    // JWT Claims
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_PURPOSE = "purpose";


    // Error Messages
    public static final String INVALID_TOKEN_PURPOSE = "Invalid token purpose";
    public static final String INVALID_OR_EXPIRED_VERIFICATION_TOKEN = "Invalid or expired verification token";
}
