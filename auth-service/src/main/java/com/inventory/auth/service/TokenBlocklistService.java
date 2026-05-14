package com.inventory.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages the JWT access-token blocklist in Redis.
 *
 * When a user logs out, their access token is added here with a TTL
 * equal to the token's remaining lifetime. Redis auto-removes it
 * when the token would have naturally expired anyway — no memory leak.
 *
 * The JwtAuthFilter checks this before accepting any token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlocklistService {

    private static final String PREFIX = "auth:blocklist:";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Add a token to the blocklist.
     *
     * @param token      raw JWT access token string
     * @param ttlSeconds remaining seconds until the token naturally expires
     */
    public void block(String token, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            // Already expired — no point adding it; filter will reject it anyway
            log.debug("Skipping blocklist for already-expired token");
            return;
        }
        redisTemplate.opsForValue()
                .set(PREFIX + token, "revoked", Duration.ofSeconds(ttlSeconds));
        log.debug("Token added to blocklist, TTL={}s", ttlSeconds);
    }

    /**
     * Check whether a token has been explicitly revoked.
     */
    public boolean isBlocked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}