package com.ecommerce.apigateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service

public class TokenBlacklistService {
    private static final String BLACKLIST_PREFIX = "blacklisted:token:";
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public TokenBlacklistService(
            @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> blacklistToken(String token, long expirationMillis) {
        String key = BLACKLIST_PREFIX + token;
        Duration ttl = Duration.ofMillis(expirationMillis);
        return redisTemplate.opsForValue()
                .set(key, "blacklisted", ttl)
                .doOnSuccess(success -> log.info("Token blacklisted: {}", token))
                .doOnError(error -> log.error("Error blacklisting token: {}", token, error));
    }

    public Mono<Boolean> isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(key);
    }

    public Mono<Boolean> removeFromBlacklist(String token) {
        String key = BLACKLIST_PREFIX + token;
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }
}
