package com.inventory.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.inventory.auth.validation.ValidationRules;

public record UpdatePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Size(
                min = ValidationRules.PASSWORD_MIN_LENGTH,
                max = ValidationRules.PASSWORD_MAX_LENGTH,
                message = ValidationRules.PASSWORD_LENGTH_MESSAGE
        )
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(
                min = ValidationRules.PASSWORD_MIN_LENGTH,
                max = ValidationRules.PASSWORD_MAX_LENGTH,
                message = ValidationRules.PASSWORD_LENGTH_MESSAGE
        )
        @Pattern(
                regexp = ValidationRules.STRONG_PASSWORD_REGEX,
                message = ValidationRules.STRONG_PASSWORD_MESSAGE
        )
        String newPassword
) {}
