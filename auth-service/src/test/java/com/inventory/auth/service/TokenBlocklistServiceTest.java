package com.inventory.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenBlocklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void block_withPositiveTtlStoresRevokedMarker() {
        TokenBlocklistService service = new TokenBlocklistService(redisTemplate);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        service.block("access", 30);

        verify(valueOperations).set("auth:blocklist:access", "revoked", Duration.ofSeconds(30));
    }

    @Test
    void block_withExpiredTokenSkipsRedisWrite() {
        TokenBlocklistService service = new TokenBlocklistService(redisTemplate);

        service.block("access", 0);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isBlocked_checksRedisKey() {
        TokenBlocklistService service = new TokenBlocklistService(redisTemplate);
        given(redisTemplate.hasKey("auth:blocklist:abc")).willReturn(true);

        assertThat(service.isBlocked("abc")).isTrue();
    }
}
