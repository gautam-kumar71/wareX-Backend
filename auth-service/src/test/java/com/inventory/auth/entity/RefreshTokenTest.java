package com.inventory.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void prePersist_setsCreatedAtAndBuilderDefaults() {
        RefreshToken token = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        token.prePersist();

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getCreatedAt()).isNotNull();
    }
}
