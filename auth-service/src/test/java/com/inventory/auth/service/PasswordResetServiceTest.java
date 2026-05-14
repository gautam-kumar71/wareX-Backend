package com.inventory.auth.service;

import com.inventory.auth.dto.request.ForgotPasswordOtpRequest;
import com.inventory.auth.dto.request.ResetPasswordWithOtpRequest;
import com.inventory.auth.entity.PasswordResetOtp;
import com.inventory.auth.entity.User;
import com.inventory.auth.enums.Role;
import com.inventory.auth.exception.InvalidOtpException;
import com.inventory.auth.repository.PasswordResetOtpRepository;
import com.inventory.auth.repository.RefreshTokenRepository;
import com.inventory.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthMailService authMailService;

    @InjectMocks
    private PasswordResetService service;

    @Test
    void requestOtp_unknownEmail_returnsSilently() {
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10);
        given(userRepo.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        service.requestOtp(new ForgotPasswordOtpRequest("ghost@test.com"));

        verify(passwordResetOtpRepository, never()).save(any());
        verify(authMailService, never()).sendPasswordResetOtp(any(), any(), any(Integer.class));
    }

    @Test
    void requestOtp_disabledUser_returnsSilently() {
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10);
        User user = user(false);
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));

        service.requestOtp(new ForgotPasswordOtpRequest("john@test.com"));

        verify(passwordResetOtpRepository, never()).save(any());
        verify(authMailService, never()).sendPasswordResetOtp(any(), any(), any(Integer.class));
    }

    @Test
    void requestOtp_activeUser_savesOtpAndSendsMail() {
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10);
        User user = user(true);
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));

        service.requestOtp(new ForgotPasswordOtpRequest(" JOHN@test.com "));

        ArgumentCaptor<PasswordResetOtp> captor = ArgumentCaptor.forClass(PasswordResetOtp.class);
        verify(passwordResetOtpRepository).deleteByEmail("john@test.com");
        verify(passwordResetOtpRepository).save(captor.capture());
        PasswordResetOtp otp = captor.getValue();
        assertThat(otp.getEmail()).isEqualTo("john@test.com");
        assertThat(otp.getOtpHash()).hasSize(64);
        verify(authMailService).sendPasswordResetOtp(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(10));
    }

    @Test
    void resetPassword_unknownUser_throwsInvalidOtp() {
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordWithOtpRequest("john@test.com", "123456", "new-pass")))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void resetPassword_missingOtp_throwsInvalidOtp() {
        User user = user(true);
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(passwordResetOtpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc("john@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordWithOtpRequest("john@test.com", "123456", "new-pass")))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void resetPassword_expiredOtp_throwsHelpfulError() {
        User user = user(true);
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email("john@test.com")
                .otpHash(hash("123456"))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(passwordResetOtpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc("john@test.com"))
                .willReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordWithOtpRequest("john@test.com", "123456", "new-pass")))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_wrongOtp_throwsInvalidOtp() {
        User user = user(true);
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email("john@test.com")
                .otpHash(hash("123456"))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(passwordResetOtpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc("john@test.com"))
                .willReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordWithOtpRequest("john@test.com", "999999", "new-pass")))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void resetPassword_success_updatesPasswordConsumesOtpAndRevokesSessions() {
        User user = user(true);
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email("john@test.com")
                .otpHash(hash("123456"))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(passwordResetOtpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc("john@test.com"))
                .willReturn(Optional.of(otp));
        given(passwordEncoder.encode("new-pass")).willReturn("new-hash");

        service.resetPassword(new ResetPasswordWithOtpRequest(" john@test.com ", "123456", "new-pass"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(otp.getConsumedAt()).isNotNull();
        verify(userRepo).save(user);
        verify(passwordResetOtpRepository).save(otp);
        verify(passwordResetOtpRepository).deleteByExpiresAtBefore(any(Instant.class));
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
        verify(authMailService).sendPasswordResetSuccess(user);
    }

    @Test
    void resetPassword_emailNotificationFailureIsSwallowed() {
        User user = user(true);
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email("john@test.com")
                .otpHash(hash("123456"))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(passwordResetOtpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc("john@test.com"))
                .willReturn(Optional.of(otp));
        given(passwordEncoder.encode("new-pass")).willReturn("new-hash");
        org.mockito.Mockito.doThrow(new RuntimeException("mail down")).when(authMailService).sendPasswordResetSuccess(user);

        service.resetPassword(new ResetPasswordWithOtpRequest("john@test.com", "123456", "new-pass"));

        verify(userRepo).save(user);
    }

    private User user(boolean enabled) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("john@test.com")
                .passwordHash("old-hash")
                .fullName("John")
                .role(Role.WAREHOUSE_STAFF)
                .enabled(enabled)
                .build();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
