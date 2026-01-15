package com.ecommerce.authservice.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientInfoUtil {
    private ClientInfoUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP",
                "True-Client-IP"
        };

        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For can contain multiple IPs
                return value.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();    }
}
