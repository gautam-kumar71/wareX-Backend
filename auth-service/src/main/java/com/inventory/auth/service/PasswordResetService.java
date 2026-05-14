package com.inventory.auth.service;

import com.inventory.auth.dto.request.ForgotPasswordOtpRequest;
import com.inventory.auth.dto.request.ResetPasswordWithOtpRequest;
import com.inventory.auth.entity.PasswordResetOtp;
import com.inventory.auth.entity.User;
import com.inventory.auth.exception.InvalidOtpException;
import com.inventory.auth.repository.PasswordResetOtpRepository;
import com.inventory.auth.repository.RefreshTokenRepository;
import com.inventory.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailService authMailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.security.password-reset.otp-expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Transactional
    public void requestOtp(ForgotPasswordOtpRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> userOpt = userRepo.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email: {}", email);
            return;
        }

        User user = userOpt.get();
        if (!user.isEnabled()) {
            log.info("Password reset requested for disabled account: {}", email);
            return;
        }

        String otp = generateOtp();
        passwordResetOtpRepository.deleteByEmail(email);
        passwordResetOtpRepository.save(PasswordResetOtp.builder()
                .email(email)
                .otpHash(hash(otp))
                .expiresAt(Instant.now().plusSeconds(otpExpiryMinutes * 60L))
                .build());

        authMailService.sendPasswordResetOtp(user, otp, otpExpiryMinutes);
        log.info("Password reset OTP issued for {}", email);
    }

    @Transactional
    public void resetPassword(ResetPasswordWithOtpRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new InvalidOtpException("Invalid or expired OTP."));

        PasswordResetOtp otp = passwordResetOtpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new InvalidOtpException("Invalid or expired OTP."));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOtpException("OTP has expired. Please request a new one.");
        }

        if (!hash(request.otp()).equals(otp.getOtpHash())) {
            throw new InvalidOtpException("Invalid or expired OTP.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepo.save(user);

        otp.setConsumedAt(Instant.now());
        passwordResetOtpRepository.save(otp);
        passwordResetOtpRepository.deleteByExpiresAtBefore(Instant.now());

        UUID userId = user.getId();
        refreshTokenRepository.revokeAllByUserId(userId);

        try {
            authMailService.sendPasswordResetSuccess(user);
        } catch (RuntimeException ex) {
            log.warn("Password reset success email failed for {}", email, ex);
        }

        log.info("Password reset completed for {}", email);
    }

    private String normalizeEmail(String value) {
        return value.toLowerCase().trim();
    }

    private String generateOtp() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
