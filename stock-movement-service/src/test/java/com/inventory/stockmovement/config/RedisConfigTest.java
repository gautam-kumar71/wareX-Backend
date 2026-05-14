package com.inventory.stockmovement.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void redisTemplate_setsFactoryAndStringSerializers() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, String> template = new RedisConfig().redisTemplate(factory);

        assertThat(template.getConnectionFactory()).isSameAs(factory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(StringRedisSerializer.class);
    }
}
