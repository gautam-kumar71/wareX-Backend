package com.inventory.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken,

        // userId must be supplied so we can scope the DB query to this user only
        @NotBlank(message = "User ID is required")
        String userId
) {}