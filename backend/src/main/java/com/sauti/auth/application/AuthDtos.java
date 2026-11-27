package com.sauti.auth.application;

import com.sauti.auth.domain.PasswordPolicy;
import com.sauti.auth.domain.User;
import com.sauti.tenant.TenantDtos.TenantResponse;
import com.sauti.tenant.application.WorkspaceViews.WorkspaceMembershipView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for the account and session endpoints. */
public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String businessName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String password
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password) {
    }

    public record GoogleLoginRequest(
            @NotBlank String idToken,
            @Size(max = 120) String businessName,
            @Size(min = 2, max = 2) String countryCode
    ) {
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
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword
    ) {
    }

    /** {@code currentPassword} may be empty only for an account that has never had a password. */
    public record ChangePasswordRequest(
            @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword
    ) {
    }

    public record RememberWorkspaceRequest(@NotNull UUID workspaceId) {
    }

    /** {@code password} for accounts that have one; otherwise {@code confirmation} must be the confirmation phrase. */
    /** {@code closeOwnedWorkspaces} must be true when the account is the only Owner of any open workspace. */
    public record DeleteAccountRequest(@Size(max = PasswordPolicy.MAX_LENGTH) String password, @Size(max = 40) String confirmation,
                                       boolean closeOwnedWorkspaces) {
    }

    public record AcceptInvitationRequest(@NotBlank String token) {
    }

    public record AdminChallengeRequest(@NotBlank String challenge) {
    }

    public record AdminCodeRequest(@NotBlank String challenge, @NotBlank @Size(min = 6, max = 6) String code) {
    }

    public record AcceptInvitationAsNewAccountRequest(
            @NotBlank String token,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String password
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

    public record ConsoleAppearanceRequest(
            @NotBlank @Size(max = 16) String mode,
            @NotBlank @Size(max = 16) String accent
    ) {
    }

    public record ConsoleAppearanceResponse(String mode, String accent) {
        public static ConsoleAppearanceResponse from(User user) {
            return new ConsoleAppearanceResponse(user.getConsoleThemeMode(), user.getConsoleThemeAccent());
        }
    }

    /** The signed-in person and every workspace they can open. */
    public record AccountResponse(
            UUID id,
            String email,
            /** The name colleagues see, or null when the person hasn't set one. */
            String displayName,
            boolean emailVerified,
            boolean hasPassword,
            boolean platformAdmin,
            UUID lastWorkspaceId,
            List<WorkspaceMembershipView> workspaces
    ) {
    }

    /**
     * Returned by sign-in and refresh. The refresh token is never in the body: it is set as an
     * HttpOnly cookie that page scripts cannot read.
     */
    /** {@code platformAdminSession} is true only for sessions started with an authenticator code. */
    public record DisplayNameRequest(String displayName) {
    }

    public record SessionResponse(String accessToken, long expiresInSeconds, AccountResponse account,
                                  boolean platformAdminSession) {
    }
}
