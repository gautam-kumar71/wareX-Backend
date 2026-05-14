package com.inventory.stockmovement.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    @Test
    void isAlreadyProcessed_returnsTrueOnlyForTrueRedisKey() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.hasKey("stock-movement:processed:evt-1")).thenReturn(true);
        when(redisTemplate.hasKey("stock-movement:processed:evt-2")).thenReturn(false);
        IdempotencyService service = new IdempotencyService(redisTemplate);

        assertThat(service.isAlreadyProcessed("evt-1")).isTrue();
        assertThat(service.isAlreadyProcessed("evt-2")).isFalse();
    }

    @Test
    void markProcessed_storesValueWithTtl() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        IdempotencyService service = new IdempotencyService(redisTemplate);

        service.markProcessed("evt-1");

        verify(ops).set("stock-movement:processed:evt-1", "1", Duration.ofHours(24));
    }
}
