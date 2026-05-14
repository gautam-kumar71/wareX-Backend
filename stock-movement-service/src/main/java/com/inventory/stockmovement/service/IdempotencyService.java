package com.inventory.stockmovement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed idempotency guard for Kafka event consumers.
 *
 * Stores processed eventIds with a 24-hour TTL.
 * After 24 hours the key expires automatically — Kafka guarantees
 * at-most-once redelivery within the retention period, and our
 * DB unique constraint handles anything that slips through after TTL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String PREFIX     = "stock-movement:processed:";
    private static final Duration EVENT_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;

    public boolean isAlreadyProcessed(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + eventId));
    }

    public void markProcessed(String eventId) {
        redisTemplate.opsForValue().set(PREFIX + eventId, "1", EVENT_TTL);
    }
}