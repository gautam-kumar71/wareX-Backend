package com.inventory.auth.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AccountTest {

    @Test
    void prePersist_setsCreatedAt() {
        OAuth2Account account = OAuth2Account.builder()
                .userId(UUID.randomUUID())
                .provider("google")
                .providerId("sub-1")
                .email("john@test.com")
                .build();

        account.prePersist();

        assertThat(account.getCreatedAt()).isNotNull();
    }
}
