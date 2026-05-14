package com.inventory.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuth2TokenRequest(

        @NotBlank(message = "Google ID token is required")
        String idToken
) {}