package com.inventory.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.inventory.auth.validation.ValidationRules;

public record ResetPasswordWithOtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be a 6-digit code")
        String otp,

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
