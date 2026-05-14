package com.inventory.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetOtpTest {

    @Test
    void prePersist_setsCreatedAt() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email("john@test.com")
                .otpHash("hash")
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        otp.prePersist();

        assertThat(otp.getCreatedAt()).isNotNull();
    }
}
