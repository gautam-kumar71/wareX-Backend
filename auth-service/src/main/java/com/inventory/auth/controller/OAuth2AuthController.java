package com.inventory.auth.controller;

import com.inventory.auth.dto.request.OAuth2TokenRequest;
import com.inventory.auth.dto.response.ApiResponse;
import com.inventory.auth.dto.response.AuthResponse;
import com.inventory.auth.service.OAuth2AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2", description = "Google OAuth2 sign-in")
public class OAuth2AuthController {

    private final OAuth2AuthService oauth2AuthService;

    /**
     * Endpoint called by the Angular frontend after the user completes
     * Google One Tap or the Google Sign-In button flow.
     *
     * Frontend flow:
     *   1. User clicks "Sign in with Google"
     *   2. Google Identity Services SDK returns an ID token (credential)
     *   3. Angular POSTs { "idToken": "<google-id-token>" } to this endpoint
     *   4. We verify the token server-side with Google's public keys
     *   5. We find or create the user, then return our own JWT pair
     *
     * The ID token is a signed JWT from Google — we verify it with the
     * google-api-client library, not just decode it.
     */
    @PostMapping("/google")
    @Operation(
            summary = "Sign in with Google",
            description = "Accepts a Google ID token from the frontend, verifies it with "
                    + "Google's public keys, and returns our own JWT access + refresh token pair."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody OAuth2TokenRequest req) {

        AuthResponse response = oauth2AuthService.loginWithGoogle(req);
        return ResponseEntity.ok(
                ApiResponse.success(response, "Google authentication successful"));
    }
}