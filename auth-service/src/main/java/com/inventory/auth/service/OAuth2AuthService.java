package com.inventory.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.inventory.auth.dto.request.OAuth2TokenRequest;
import com.inventory.auth.dto.response.AuthResponse;
import com.inventory.auth.entity.OAuth2Account;
import com.inventory.auth.entity.User;
import com.inventory.auth.enums.Role;
import com.inventory.auth.exception.OAuth2AuthenticationException;
import com.inventory.auth.repository.OAuth2AccountRepository;
import com.inventory.auth.repository.UserRepository;
import com.inventory.auth.security.oauth2.GoogleOAuth2UserInfo;
import com.inventory.auth.security.oauth2.GoogleTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthService {

    private final GoogleTokenVerifier    googleTokenVerifier;
    private final UserRepository         userRepo;
    private final OAuth2AccountRepository oauthRepo;
    private final AuthService            authService;

    private static final String PROVIDER = "google";

    // ─────────────────────────────────────────────────────────────────────────
    // Google Login Entry Point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies a Google ID token from the Angular frontend and returns our own
     * JWT pair. Handles three cases:
     *   1. Returning Google user     → issue new tokens, refresh picture URL
     *   2. New user, new email       → create User + OAuth2Account, issue tokens
     *   3. Existing password user    → link OAuth2Account to existing User, issue tokens
     */
    @Transactional
    public AuthResponse loginWithGoogle(OAuth2TokenRequest req) {
        // Step 1 — verify token with Google's public keys
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(req.idToken());
        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(payload);

        // Step 2 — reject unverified emails
        // Google can return unverified emails for legacy migrated accounts
        if (!userInfo.emailVerified()) {
            throw new OAuth2AuthenticationException(
                    "Google account email '%s' is not verified. Please verify your email with Google first."
                            .formatted(userInfo.email()));
        }

        // Step 3 — find-or-create flow
        return oauthRepo.findByProviderAndProviderId(PROVIDER, userInfo.sub())
                .map(existingOAuthAccount -> handleReturningUser(existingOAuthAccount, userInfo))
                .orElseGet(() -> handleNewOAuth2Login(userInfo));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Returning user — already linked their Google account
    // ─────────────────────────────────────────────────────────────────────────

    private AuthResponse handleReturningUser(OAuth2Account oauthAccount,
                                             GoogleOAuth2UserInfo userInfo) {
        User user = userRepo.findById(oauthAccount.getUserId())
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        "Linked user account not found. Please contact support."));

        if (!user.isEnabled()) {
            throw new OAuth2AuthenticationException(
                    "Your account has been disabled. Please contact support.");
        }

        // Refresh picture URL if the user updated their Google profile photo
        if (userInfo.pictureUrl() != null
                && !userInfo.pictureUrl().equals(oauthAccount.getPictureUrl())) {
            oauthAccount.setPictureUrl(userInfo.pictureUrl());
            oauthRepo.save(oauthAccount);
        }

        log.info("Google OAuth2 login (returning user): {}", user.getEmail());
        return authService.buildTokensForUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // New Google login — no existing OAuth2Account for this sub
    // ─────────────────────────────────────────────────────────────────────────

    private AuthResponse handleNewOAuth2Login(GoogleOAuth2UserInfo userInfo) {
        String normalizedEmail = userInfo.email().toLowerCase().trim();

        // Case A — email already exists (registered via password before)
        // → silently link Google account to existing user
        // Case B — brand new user → create User row + link Google account
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseGet(() -> createUserFromGoogle(userInfo, normalizedEmail));

        if (!user.isEnabled()) {
            throw new OAuth2AuthenticationException(
                    "Your account has been disabled. Please contact support.");
        }

        linkGoogleAccount(user, userInfo);

        log.info("Google OAuth2 login (new link): email={}, sub={}", normalizedEmail, userInfo.sub());
        return authService.buildTokensForUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create brand-new user from Google profile
    // ─────────────────────────────────────────────────────────────────────────

    private User createUserFromGoogle(GoogleOAuth2UserInfo userInfo, String normalizedEmail) {
        String fullName = (userInfo.name() != null && !userInfo.name().isBlank())
                ? userInfo.name().trim()
                : normalizedEmail;  // fallback if Google doesn't share name

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(null)         // OAuth2 user — no password ever
                .fullName(fullName)
                .role(Role.WAREHOUSE_STAFF)           // default role; admin can elevate later
                .build();

        userRepo.save(user);
        log.info("Created new user from Google OAuth2: {}", normalizedEmail);
        return user;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Link an OAuth2Account row to an existing User
    // ─────────────────────────────────────────────────────────────────────────

    private void linkGoogleAccount(User user, GoogleOAuth2UserInfo userInfo) {
        // Guard against race condition: two simultaneous first-time logins
        // The unique constraint on (provider, provider_id) is the last line of defence,
        // but we check first to give a clean log entry rather than a DB exception
        boolean alreadyLinked = oauthRepo
                .findByProviderAndProviderId(PROVIDER, userInfo.sub())
                .isPresent();

        if (alreadyLinked) {
            log.debug("OAuth2Account already linked for sub={} — skipping duplicate insert",
                    userInfo.sub());
            return;
        }

        OAuth2Account account = OAuth2Account.builder()
                .userId(user.getId())
                .provider(PROVIDER)
                .providerId(userInfo.sub())
                .email(userInfo.email())
                .pictureUrl(userInfo.pictureUrl())
                .build();

        oauthRepo.save(account);
        log.debug("Linked Google account sub={} to user {}", userInfo.sub(), user.getId());
    }
}
