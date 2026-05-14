package com.inventory.auth.config;

import com.inventory.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Nightly cleanup job that purges expired refresh token rows from the DB.
 * Without this, the refresh_tokens table grows indefinitely.
 *
 * Redis blocklist entries self-expire via TTL — no cleanup needed there.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {

    private final RefreshTokenRepository rtRepo;

    /**
     * Runs every day at 02:00 AM server time.
     * Deletes tokens that expired more than 24 hours ago.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now().minusSeconds(86_400); // 24 h buffer
        rtRepo.deleteExpiredBefore(cutoff);
        log.info("Purged expired refresh tokens older than {}", cutoff);
    }
}