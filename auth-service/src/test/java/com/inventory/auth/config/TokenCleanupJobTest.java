package com.inventory.auth.config;

import com.inventory.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenCleanupJobTest {

    @Mock
    private RefreshTokenRepository rtRepo;

    @InjectMocks
    private TokenCleanupJob job;

    @Test
    void purgeExpiredTokens_deletesBeforeRecentCutoff() {
        Instant before = Instant.now().minusSeconds(86_410);
        Instant after = Instant.now().minusSeconds(86_390);

        job.purgeExpiredTokens();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(rtRepo).deleteExpiredBefore(captor.capture());
        assertThat(captor.getValue()).isBetween(before, after);
    }
}
