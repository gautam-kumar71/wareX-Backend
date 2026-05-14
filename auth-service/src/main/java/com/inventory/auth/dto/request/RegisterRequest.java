package com.inventory.auth.dto.request;

import com.inventory.auth.enums.Role;
import com.inventory.auth.validation.ValidationRules;
import jakarta.validation.constraints.*;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2–100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(
                min = ValidationRules.PASSWORD_MIN_LENGTH,
                max = ValidationRules.PASSWORD_MAX_LENGTH,
                message = ValidationRules.PASSWORD_LENGTH_MESSAGE
        )
        @Pattern(
                regexp = ValidationRules.STRONG_PASSWORD_REGEX,
                message = ValidationRules.STRONG_PASSWORD_MESSAGE
        )
        String password,

        // Only ADMIN can assign roles; service defaults to STAFF if null
        Role role
) {}
