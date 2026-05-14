package com.inventory.auth.service;

import com.inventory.auth.dto.request.LoginRequest;
import com.inventory.auth.dto.request.RefreshRequest;
import com.inventory.auth.dto.request.RegisterRequest;
import com.inventory.auth.dto.request.UpdatePasswordRequest;
import com.inventory.auth.dto.request.UpdateProfileRequest;
import com.inventory.auth.dto.response.AuthResponse;
import com.inventory.auth.dto.response.UserInfoResponse;
import com.inventory.auth.entity.RefreshToken;
import com.inventory.auth.entity.User;
import com.inventory.auth.enums.Role;
import com.inventory.auth.exception.TokenException;
import com.inventory.auth.exception.UserAlreadyExistsException;
import com.inventory.auth.repository.RefreshTokenRepository;
import com.inventory.auth.repository.UserRepository;
import com.inventory.auth.security.JwtProvider;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository         userRepo;
    @Mock RefreshTokenRepository rtRepo;
    @Mock PasswordEncoder        passwordEncoder;
    @Mock JwtProvider            jwtProvider;
    @Mock TokenBlocklistService  blocklist;
    @InjectMocks AuthService     authService;

    // ─── Register ─────────────────────────────────────────────────────────────

    @Test
    void register_newUser_savesUserAndReturnsTokenPair() {
        given(userRepo.existsByEmail("john@test.com")).willReturn(false);
        given(userRepo.count()).willReturn(1L); // Non-zero so it defaults to WAREHOUSE_STAFF
        given(passwordEncoder.encode("SecurePass1!")).willReturn("$2a$hashed");
        given(jwtProvider.generateAccessToken(any())).willReturn("access.tok");
        given(jwtProvider.generateRefreshToken()).willReturn("refresh-uuid");
        given(jwtProvider.getAccessExpiryMs()).willReturn(900_000L);
        given(jwtProvider.getRefreshExpiryMs()).willReturn(604_800_000L);

        AuthResponse resp = authService.register(
                new RegisterRequest("John Doe", "john@test.com", "SecurePass1!", null));

        assertThat(resp.accessToken()).isEqualTo("access.tok");
        assertThat(resp.refreshToken()).isEqualTo("refresh-uuid");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.expiresIn()).isEqualTo(900L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("john@test.com");
        assertThat(saved.getRole()).isEqualTo(Role.WAREHOUSE_STAFF);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");

        verify(rtRepo).save(any(RefreshToken.class));
    }

    @Test
    void register_emailNormalized_toLowercase() {
        given(userRepo.existsByEmail("john@test.com")).willReturn(false);
        given(userRepo.count()).willReturn(1L);
        given(passwordEncoder.encode(any())).willReturn("hashed");
        given(jwtProvider.generateAccessToken(any())).willReturn("tok");
        given(jwtProvider.generateRefreshToken()).willReturn("ref");
        given(jwtProvider.getAccessExpiryMs()).willReturn(900_000L);
        given(jwtProvider.getRefreshExpiryMs()).willReturn(604_800_000L);

        authService.register(new RegisterRequest("John", "JOHN@TEST.COM", "SecurePass1!", null));

        verify(userRepo).save(argThat(u -> u.getEmail().equals("john@test.com")));
    }

    @Test
    void register_duplicateEmail_throwsUserAlreadyExists() {
        given(userRepo.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("John", "dup@test.com", "SecurePass1!", null)))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("dup@test.com");
    }

    @Test
    void register_withAdminRole_setsAdminRole() {
        given(userRepo.existsByEmail(any())).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("hashed");
        given(jwtProvider.generateAccessToken(any())).willReturn("tok");
        given(jwtProvider.generateRefreshToken()).willReturn("ref");
        given(jwtProvider.getAccessExpiryMs()).willReturn(900_000L);
        given(jwtProvider.getRefreshExpiryMs()).willReturn(604_800_000L);

        authService.register(new RegisterRequest("Admin", "admin@test.com", "Pass1!", Role.ADMIN));

        verify(userRepo).save(argThat(u -> u.getRole() == Role.ADMIN));
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTokenPair() {
        User user = buildUser();
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pass", "$2a$hashed")).willReturn(true);
        given(jwtProvider.generateAccessToken(user)).willReturn("access.tok");
        given(jwtProvider.generateRefreshToken()).willReturn("refresh-uuid");
        given(jwtProvider.getAccessExpiryMs()).willReturn(900_000L);
        given(jwtProvider.getRefreshExpiryMs()).willReturn(604_800_000L);

        AuthResponse resp = authService.login(new LoginRequest("john@test.com", "pass"));

        assertThat(resp.accessToken()).isEqualTo("access.tok");
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        given(userRepo.findByEmail(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("ghost@test.com", "pass")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        given(userRepo.findByEmail(any())).willReturn(Optional.of(buildUser()));
        given(passwordEncoder.matches(any(), any())).willReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("john@test.com", "wrongpass")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_disabledUser_throwsBadCredentials() {
        User user = buildUser();
        user.setEnabled(false);
        given(userRepo.findByEmail(any())).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("john@test.com", "pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void login_oauthOnlyUser_throwsBadCredentials() {
        User oauthUser = buildUser();
        oauthUser.setPasswordHash(null);
        given(userRepo.findByEmail(any())).willReturn(Optional.of(oauthUser));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("john@test.com", "pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Google sign-in");
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    @Test
    void refresh_validToken_rotatesAndReturnsNewPair() {
        UUID userId = UUID.randomUUID();
        User user   = buildUser();
        user = User.builder().id(userId).email("j@t.com")
                .passwordHash("hashed").fullName("J").role(Role.WAREHOUSE_STAFF).enabled(true).build();

        String rawRefreshToken = "plain-refresh-token";
        RefreshToken rt = RefreshToken.builder()
                .id(1L).userId(userId).tokenHash(hashToken(rawRefreshToken))
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();

        given(rtRepo.findByTokenHash(hashToken(rawRefreshToken))).willReturn(Optional.of(rt));
        given(userRepo.findById(userId)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(user)).willReturn("new.access");
        given(jwtProvider.generateRefreshToken()).willReturn("new-refresh");
        given(jwtProvider.getAccessExpiryMs()).willReturn(900_000L);
        given(jwtProvider.getRefreshExpiryMs()).willReturn(604_800_000L);

        AuthResponse resp = authService.refresh(
                new RefreshRequest(rawRefreshToken, userId.toString()));

        assertThat(resp.accessToken()).isEqualTo("new.access");
        // Old token must be revoked
        verify(rtRepo).save(argThat(RefreshToken::isRevoked));
    }

    @Test
    void refresh_invalidToken_throwsTokenException() {
        UUID userId = UUID.randomUUID();
        given(rtRepo.findByTokenHash(hashToken("wrong-token"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(
                new RefreshRequest("wrong-token", userId.toString())))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void refresh_noValidTokensForUser_throwsTokenException() {
        UUID userId = UUID.randomUUID();
        given(rtRepo.findByTokenHash(hashToken("any-token"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(
                new RefreshRequest("any-token", userId.toString())))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void refresh_invalidUserIdFormat_throwsTokenException() {
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("token", "not-a-uuid")))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Invalid user ID format");
    }

    @Test
    void refresh_disabledUser_throwsTokenException() {
        UUID userId = UUID.randomUUID();
        User user = buildUser();
        user.setId(userId);
        user.setEnabled(false);

        RefreshToken rt = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken("refresh-token"))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        given(rtRepo.findByTokenHash(hashToken("refresh-token"))).willReturn(Optional.of(rt));
        given(userRepo.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token", userId.toString())))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("disabled");
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_blocksTokenAndRevokesAllSessions() {
        Claims claims = mock(Claims.class);
        given(claims.getExpiration())
                .willReturn(Date.from(Instant.now().plusSeconds(600)));
        given(jwtProvider.validateAndExtract("access.tok")).willReturn(claims);

        UUID userId = UUID.randomUUID();
        authService.logout("access.tok", userId.toString());

        verify(blocklist).block(eq("access.tok"), longThat(ttl -> ttl > 0 && ttl <= 600));
        verify(rtRepo).revokeAllByUserId(userId);
    }

    @Test
    void logout_expiredToken_stillRevokesRefreshTokens() {
        // Even if the access token is already expired, we still revoke all refresh tokens
        given(jwtProvider.validateAndExtract(any()))
                .willThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));

        UUID userId = UUID.randomUUID();
        authService.logout("expired.tok", userId.toString());

        // Blocklist NOT called (token already useless)
        verify(blocklist, never()).block(any(), anyLong());
        // But refresh tokens ARE still revoked
        verify(rtRepo).revokeAllByUserId(userId);
    }

    // ─── Me / Admin / Profile ────────────────────────────────────────────────

    @Test
    void getMe_returnsCurrentUserProfile() {
        User user = buildUser();
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));

        UserInfoResponse response = authService.getMe(user.getId().toString());

        assertThat(response.email()).isEqualTo("john@test.com");
        assertThat(response.isOAuth2User()).isFalse();
    }

    @Test
    void getMe_disabledUser_throwsBadCredentials() {
        User user = buildUser();
        user.setEnabled(false);
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.getMe(user.getId().toString()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void disableUser_disablesAccountAndRevokesTokens() {
        User user = buildUser();
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));

        authService.disableUser(user.getId().toString());

        assertThat(user.isEnabled()).isFalse();
        verify(userRepo).save(user);
        verify(rtRepo).revokeAllByUserId(user.getId());
    }

    @Test
    void enableUser_reEnablesAccount() {
        User user = buildUser();
        user.setEnabled(false);
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));

        authService.enableUser(user.getId().toString());

        assertThat(user.isEnabled()).isTrue();
        verify(userRepo).save(user);
    }

    @Test
    void updateUserRole_persistsRoleAndRevokesTokens() {
        User user = buildUser();
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));

        authService.updateUserRole(user.getId().toString(), Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(rtRepo).revokeAllByUserId(user.getId());
    }

    @Test
    void getAllUsers_mapsUsersToResponse() {
        User localUser = buildUser();
        User oauthUser = buildUser();
        oauthUser.setId(UUID.randomUUID());
        oauthUser.setEmail("oauth@test.com");
        oauthUser.setPasswordHash(null);

        given(userRepo.findAll()).willReturn(java.util.List.of(localUser, oauthUser));

        assertThat(authService.getAllUsers())
                .hasSize(2)
                .extracting(UserInfoResponse::isOAuth2User)
                .containsExactly(false, true);
    }

    @Test
    void updateProfile_trimsAndReturnsUpdatedUser() {
        User user = buildUser();
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));

        UserInfoResponse response = authService.updateProfile(
                user.getId().toString(),
                new UpdateProfileRequest("  Jane Doe  ")
        );

        assertThat(user.getFullName()).isEqualTo("Jane Doe");
        assertThat(response.fullName()).isEqualTo("Jane Doe");
    }

    @Test
    void updatePassword_changesHashAndRevokesTokens() {
        User user = buildUser();
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
        given(passwordEncoder.matches("old-pass", "$2a$hashed")).willReturn(true);
        given(passwordEncoder.encode("new-pass")).willReturn("new-hash");

        authService.updatePassword(user.getId().toString(), new UpdatePasswordRequest("old-pass", "new-pass"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(rtRepo).revokeAllByUserId(user.getId());
    }

    @Test
    void updatePassword_forOauthUser_throwsBadCredentials() {
        User user = buildUser();
        user.setPasswordHash(null);
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.updatePassword(
                user.getId().toString(), new UpdatePasswordRequest("old-pass", "new-pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Google");
    }

    @Test
    void updatePassword_wrongCurrentPassword_throwsBadCredentials() {
        User user = buildUser();
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "$2a$hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.updatePassword(
                user.getId().toString(), new UpdatePasswordRequest("wrong", "new-pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Current password");
    }

    @Test
    void updatePassword_sameAsCurrent_throwsBadCredentials() {
        User user = buildUser();
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
        given(passwordEncoder.matches("SamePass1!", "$2a$hashed")).willReturn(true);

        assertThatThrownBy(() -> authService.updatePassword(
                user.getId().toString(), new UpdatePasswordRequest("SamePass1!", "SamePass1!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("different");
    }

    @Test
    void buildTokensForUser_persistsHashedRefreshTokenAndReturnsSeconds() {
        User user = buildUser();
        given(jwtProvider.generateAccessToken(user)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtProvider.getAccessExpiryMs()).willReturn(120_000L);
        given(jwtProvider.getRefreshExpiryMs()).willReturn(600_000L);

        AuthResponse response = authService.buildTokensForUser(user);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresIn()).isEqualTo(120L);
        verify(rtRepo).save(argThat(token -> token.getTokenHash().equals(hashToken("refresh-token"))));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("john@test.com")
                .passwordHash("$2a$hashed")
                .fullName("John Doe")
                .role(Role.WAREHOUSE_STAFF)
                .enabled(true)
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
