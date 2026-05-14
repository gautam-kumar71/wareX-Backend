package com.inventory.auth.repository;

import com.inventory.auth.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(String email);

    @Modifying
    void deleteByEmail(String email);

    @Modifying
    void deleteByExpiresAtBefore(Instant cutoff);
}
