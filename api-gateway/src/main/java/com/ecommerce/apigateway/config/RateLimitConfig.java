package com.ecommerce.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {

            // 1. Rate limit theo user đã login
            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");

            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            // 2. X-Forwarded-For (LB / proxy)
            String xForwardedFor = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Forwarded-For");

            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String clientIp = xForwardedFor.split(",")[0].trim();
                return Mono.just("ip:" + clientIp);
            }

            // 3. Fallback IP (local / dev)
            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just("ip:" +
                        exchange.getRequest()
                                .getRemoteAddress()
                                .getAddress()
                                .getHostAddress());
            }

            // 4. Last fallback
            return Mono.just("anonymous");
        };
    }


}
