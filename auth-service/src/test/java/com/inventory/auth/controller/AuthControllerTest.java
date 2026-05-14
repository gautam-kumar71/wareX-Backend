package com.inventory.auth.controller;

import com.inventory.auth.dto.request.ForgotPasswordOtpRequest;
import com.inventory.auth.dto.request.LoginRequest;
import com.inventory.auth.dto.request.RefreshRequest;
import com.inventory.auth.dto.request.RegisterRequest;
import com.inventory.auth.dto.request.ResetPasswordWithOtpRequest;
import com.inventory.auth.dto.request.UpdatePasswordRequest;
import com.inventory.auth.dto.request.UpdateProfileRequest;
import com.inventory.auth.dto.response.ApiResponse;
import com.inventory.auth.dto.response.AuthResponse;
import com.inventory.auth.dto.response.UserInfoResponse;
import com.inventory.auth.enums.Role;
import com.inventory.auth.service.AuthService;
import com.inventory.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final AuthController controller = new AuthController(authService, passwordResetService);

    @Test
    void publicEndpoints_delegateAndWrapResponses() {
        AuthResponse authResponse = new AuthResponse("access", "refresh", 900);
        given(authService.register(org.mockito.ArgumentMatchers.any())).willReturn(authResponse);
        given(authService.login(org.mockito.ArgumentMatchers.any())).willReturn(authResponse);
        given(authService.refresh(org.mockito.ArgumentMatchers.any())).willReturn(authResponse);

        assertThat(controller.register(new RegisterRequest("John", "john@test.com", "pass", null)).getStatusCodeValue()).isEqualTo(201);
        assertThat(controller.login(new LoginRequest("john@test.com", "pass")).getBody().message()).isEqualTo("Login successful");
        assertThat(controller.refresh(new RefreshRequest("refresh", "user-id")).getBody().message()).isEqualTo("Token refreshed successfully");
    }

    @Test
    void passwordResetEndpoints_delegate() {
        assertThat(controller.forgotPassword(new ForgotPasswordOtpRequest("john@test.com")).getBody().message()).contains("eligible account exists");
        assertThat(controller.resetPassword(new ResetPasswordWithOtpRequest("john@test.com", "123456", "new-pass")).getBody().message()).contains("Password reset successfully");
        verify(passwordResetService).requestOtp(org.mockito.ArgumentMatchers.any());
        verify(passwordResetService).resetPassword(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticatedEndpoints_delegateAndWrap() {
        UserInfoResponse user = new UserInfoResponse("1", "john@test.com", "John", Role.ADMIN, false);
        given(authService.getMe("1")).willReturn(user);
        given(authService.updateProfile(org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.any())).willReturn(user);
        given(authService.getAllUsers()).willReturn(List.of(user));

        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("Authorization")).willReturn("Bearer access-token");

        assertThat(controller.getMe("1").getBody().data()).isEqualTo(user);
        assertThat(controller.logout(request, "1").getBody().message()).isEqualTo("Logged out successfully");
        assertThat(controller.updateProfile("1", new UpdateProfileRequest("John")).getBody().data()).isEqualTo(user);
        assertThat(controller.updatePassword("1", new UpdatePasswordRequest("old", "new")).getBody().message()).contains("login again");
        assertThat(controller.disableUser("2").getBody().message()).contains("disabled");
        assertThat(controller.enableUser("2").getBody().message()).contains("enabled");
        assertThat(controller.getAllUsers().getBody().data()).hasSize(1);
        assertThat(controller.updateRole("2", Role.ADMIN).getBody().message()).contains("Role updated");
    }
}
