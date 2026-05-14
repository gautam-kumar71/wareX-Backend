package com.inventory.auth.controller;

import com.inventory.auth.dto.request.*;
import com.inventory.auth.dto.response.ApiResponse;
import com.inventory.auth.dto.response.AuthResponse;
import com.inventory.auth.dto.response.UserInfoResponse;
import com.inventory.auth.service.AuthService;
import com.inventory.auth.service.PasswordResetService;
import com.inventory.auth.enums.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh, logout")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    // ─────────────────────────────────────────────────────────────────────────
    // Public endpoints — no token required
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new user account",
            description = "Creates a new account and returns a JWT token pair immediately.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest req) {

        AuthResponse response = authService.register(req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account created successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password",
            description = "Returns an access token (15 min) and a refresh token (7 days).")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req) {

        AuthResponse response = authService.login(req);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token",
            description = "Exchanges a valid refresh token for a new token pair. "
                    + "The old refresh token is immediately revoked (rotation).")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest req) {

        AuthResponse response = authService.refresh(req);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset OTP",
            description = "Sends a one-time password to the user's email address if the account is eligible.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordOtpRequest req) {

        passwordResetService.requestOtp(req);
        return ResponseEntity.ok(ApiResponse.success(null,
                "If an eligible account exists for that email, an OTP has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using email OTP",
            description = "Validates the emailed OTP and updates the account password.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordWithOtpRequest req) {

        passwordResetService.resetPassword(req);
        return ResponseEntity.ok(ApiResponse.success(null,
                "Password reset successfully. Please login with your new password."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Authenticated endpoints — valid Bearer token required
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get current user profile",
            description = "Returns identity information for the currently authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserInfoResponse>> getMe(
            @AuthenticationPrincipal String userId) {

        UserInfoResponse response = authService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "User profile retrieved successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke all sessions",
            description = "Blocklists the access token and revokes all refresh tokens for the user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            @AuthenticationPrincipal String userId) {

        String authHeader = request.getHeader("Authorization");
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;
        authService.logout(token, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserInfoResponse>> updateProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdateProfileRequest req) {

        UserInfoResponse response = authService.updateProfile(userId, req);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
    }

    @PatchMapping("/password")
    @Operation(summary = "Update current user password",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdatePasswordRequest req) {

        authService.updatePassword(userId, req);
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated successfully. Please login again."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin endpoints — ADMIN role required
    // ─────────────────────────────────────────────────────────────────────────

    @PatchMapping("/admin/users/{userId}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Disable a user account (Admin only)",
            description = "Disables the account and immediately revokes all their sessions.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> disableUser(
            @PathVariable String userId) {

        authService.disableUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User disabled successfully"));
    }

    @PatchMapping("/admin/users/{userId}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Re-enable a disabled user account (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> enableUser(
            @PathVariable String userId) {

        authService.enableUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User enabled successfully"));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<java.util.List<com.inventory.auth.dto.response.UserInfoResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(authService.getAllUsers(), "User list retrieved"));
    }

    @PutMapping("/admin/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user role (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> updateRole(
            @PathVariable String userId,
            @RequestParam Role role) {
        authService.updateUserRole(userId, role);
        return ResponseEntity.ok(ApiResponse.success(null, "Role updated successfully"));
    }
}
