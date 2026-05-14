package com.inventory.auth.security.oauth2;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
/**
 * Strongly-typed wrapper around Google ID token claims.
 * Centralises claim key strings so typos don't scatter across services.
 */
public record GoogleOAuth2UserInfo(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String pictureUrl
) {
    public GoogleOAuth2UserInfo(GoogleIdToken.Payload payload) {
        this(
                payload.getSubject(),
                payload.getEmail(),
                Boolean.TRUE.equals(payload.getEmailVerified()),
                (String) payload.get("name"),
                (String) payload.get("picture")
        );
    }
}
