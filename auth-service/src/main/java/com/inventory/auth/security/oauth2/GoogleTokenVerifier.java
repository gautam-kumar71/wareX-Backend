package com.inventory.auth.security.oauth2;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.inventory.auth.exception.OAuth2AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Slf4j
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(
            @Value("${spring.security.oauth2.client.registration.google.client-id}")
            String clientId) {

        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                // Clock skew tolerance: Google tokens can be up to 5 min off
                .setAcceptableTimeSkewSeconds(300)
                .build();
        log.info("GoogleTokenVerifier initialized with Client ID: {}", clientId);
    }

    /**
     * Verifies the Google ID token against Google's public keys.
     *
     * Validates:
     *  - Signature (Google's RS256 public key)
     *  - Expiry (exp claim)
     *  - Audience (must match our client-id)
     *  - Issuer (must be accounts.google.com or https://accounts.google.com)
     *
     * @throws OAuth2AuthenticationException if the token is invalid for any reason
     */
    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.warn("Google ID token verification failed (returned null). Check Client ID mismatch or token expiry.");
                throw new OAuth2AuthenticationException(
                        "Google ID token is invalid, expired, or was not issued for this application");
            }

            log.debug("Google ID token verified for sub={}", idToken.getPayload().getSubject());
            return idToken.getPayload();

        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during Google token verification", ex);
            throw new OAuth2AuthenticationException(
                    "Google token verification failed: " + ex.getMessage());
        }
    }
}