package com.inventory.auth.service;

import com.inventory.auth.dto.request.*;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepo;
    private final RefreshTokenRepository rtRepo;
    private final PasswordEncoder        passwordEncoder;
    private final JwtProvider            jwtProvider;
    private final TokenBlocklistService  blocklist;

    // ─────────────────────────────────────────────────────────────────────────
    // Register
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String normalizedEmail = req.email().toLowerCase().trim();

        if (userRepo.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException(
                    "An account with email '%s' already exists".formatted(normalizedEmail));
        }

        // Production Bootstrap: The very first user to register becomes ADMIN.
        // All subsequent users default to VIEWER.
        boolean isFirstUser = userRepo.count() == 0;
        Role initialRole = isFirstUser ? Role.ADMIN : Role.WAREHOUSE_STAFF;

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .role(initialRole)
                .build();

        userRepo.save(user);
        log.info("New user registered: {} with role {}", user.getEmail(), user.getRole());

        return buildTokensForUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String normalizedEmail = req.email().toLowerCase().trim();

        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        // OAuth2-only users have no password — block password login attempt
        if (user.getPasswordHash() == null) {
            throw new BadCredentialsException(
                    "This account uses Google sign-in. Please use the Google login option.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        log.info("Successful login: {}", user.getEmail());
        return buildTokensForUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh Token
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        UUID userId;
        try {
            userId = UUID.fromString(req.userId());
        } catch (IllegalArgumentException ex) {
            throw new TokenException("Invalid user ID format");
        }

        // Direct lookup by SHA-256 hash
        String tokenHash = hashToken(req.refreshToken());
        RefreshToken matched = rtRepo.findByTokenHash(tokenHash)
                .filter(rt -> rt.getUserId().equals(userId))
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> rt.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new TokenException("Refresh token is invalid or expired"));

        // Token rotation — revoke old, issue new pair
        matched.setRevoked(true);
        rtRepo.save(matched);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new TokenException("User account not found"));

        if (!user.isEnabled()) {
            throw new TokenException("Account is disabled");
        }

        log.info("Token rotated for user: {}", user.getEmail());
        return buildTokensForUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String rawAccessToken, String userId) {
        // 1. Blocklist the access token for its remaining natural lifetime
        try {
            Claims claims = jwtProvider.validateAndExtract(rawAccessToken);
            long remainingTtlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            long remainingTtlSec = remainingTtlMs / 1000;
            blocklist.block(rawAccessToken, remainingTtlSec);
        } catch (Exception ex) {
            // Token already expired — still revoke refresh tokens below
            log.debug("Access token already expired at logout, skipping blocklist");
        }

        // 2. Revoke ALL refresh tokens for this user (logout from every device)
        UUID uid = UUID.fromString(userId);
        rtRepo.revokeAllByUserId(uid);
        log.info("User {} logged out — all refresh tokens revoked", userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get Current User (Me)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserInfoResponse getMe(String userId) {
        UUID uid = UUID.fromString(userId);
        User user = userRepo.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        return new UserInfoResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getPasswordHash() == null
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin — Disable User
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void disableUser(String targetUserId) {
        UUID uid = UUID.fromString(targetUserId);
        User user = userRepo.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + targetUserId));

        user.setEnabled(false);
        userRepo.save(user);

        // Force immediate logout from all devices
        rtRepo.revokeAllByUserId(uid);
        log.info("Admin disabled user: {}", targetUserId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin — Enable User
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void enableUser(String targetUserId) {
        UUID uid = UUID.fromString(targetUserId);
        User user = userRepo.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + targetUserId));

        user.setEnabled(true);
        userRepo.save(user);
        log.info("Admin enabled user: {}", targetUserId);
    }

    @Transactional
    public void updateUserRole(String targetUserId, Role newRole) {
        UUID uid = UUID.fromString(targetUserId);
        User user = userRepo.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + targetUserId));

        user.setRole(newRole);
        userRepo.save(user);
        
        // Revoke tokens so the user has to re-login with the new role
        rtRepo.revokeAllByUserId(uid);
        log.info("Admin changed role for user {} to {}", targetUserId, newRole);
    }

    @Transactional(readOnly = true)
    public List<UserInfoResponse> getAllUsers() {
        return userRepo.findAll().stream()
                .map(user -> new UserInfoResponse(
                        user.getId().toString(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole(),
                        user.getPasswordHash() == null
                )).toList();
    }

    @Transactional
    public UserInfoResponse updateProfile(String userId, UpdateProfileRequest req) {
        UUID uid = UUID.fromString(userId);
        User user = userRepo.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setFullName(req.fullName().trim());
        userRepo.save(user);

        log.info("User updated profile: {}", user.getEmail());
        return new UserInfoResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getPasswordHash() == null
        );
    }

    @Transactional
    public void updatePassword(String userId, UpdatePasswordRequest req) {
        UUID uid = UUID.fromString(userId);
        User user = userRepo.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // If user has no password hash (OAuth2 user), they cannot update password this way
        if (user.getPasswordHash() == null) {
            throw new BadCredentialsException("This account is linked to Google. Password cannot be changed manually.");
        }

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password does not match");
        }

        if (req.currentPassword().equals(req.newPassword())) {
            throw new BadCredentialsException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);

        // Revoke all sessions on password change for security
        rtRepo.revokeAllByUserId(uid);
        log.info("User updated password: {}", user.getEmail());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Package-visible helper — shared with OAuth2AuthService
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse buildTokensForUser(User user) {
        String accessToken  = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken();

        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(refreshToken))
                .expiresAt(Instant.now().plusMillis(jwtProvider.getRefreshExpiryMs()))
                .build();
        rtRepo.save(rt);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtProvider.getAccessExpiryMs() / 1000   // expose as seconds
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * SHA-256 is perfect for opaque UUID tokens. 
     * Takes microseconds vs 250ms for Bcrypt.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
