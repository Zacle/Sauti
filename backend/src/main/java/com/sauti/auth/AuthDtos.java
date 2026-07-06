package com.sauti.auth;

import com.sauti.tenant.TenantDtos.TenantResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank String businessName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @NotBlank @Size(min = 8) String password
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record GoogleLoginRequest(
            @NotBlank String idToken,
            String businessName,
            @Size(min = 2, max = 2) String countryCode
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record VerifyEmailRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 6) String code
    ) {
    }

    public record ResendVerificationRequest(@Email @NotBlank String email) {
    }

    public record ForgotPasswordRequest(@Email @NotBlank String email) {
    }

    public record ResetPasswordRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 6) String code,
            @NotBlank @Size(min = 8) String newPassword
    ) {
    }

    public record RegisterResponse(
            String status,
            String message,
            String devVerificationCode,
            TenantResponse tenant
    ) {
    }

    public record MessageResponse(String status, String message, String devCode) {
    }

    public record AuthResponse(String accessToken, String refreshToken, TenantResponse tenant) {
    }
}
