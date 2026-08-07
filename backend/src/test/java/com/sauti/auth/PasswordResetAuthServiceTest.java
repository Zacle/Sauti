package com.sauti.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.auth.AuthDtos.ResetPasswordRequest;
import com.sauti.tenant.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetAuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final VerificationCodeService codes = mock(VerificationCodeService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthService service = new AuthService(
            mock(TenantRepository.class), users, refreshTokens, mock(AuthEmailService.class),
            mock(GoogleOAuthService.class), codes, passwords, mock(JwtService.class),
            mock(ApplicationEventPublisher.class), new PlatformAdminPolicy(""), 30, 30, false, false);

    @Test
    void unknownAccountsUseTheSameInvalidCodeError() {
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("unknown@example.com", "123456", "new-password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset code");
        verify(codes, never()).verifyPasswordResetCode(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void validCodeChangesPasswordDeletesCodeAndRevokesSessions() {
        var user = mock(User.class);
        var first = mock(RefreshToken.class);
        var second = mock(RefreshToken.class);
        when(user.getId()).thenReturn(java.util.UUID.randomUUID());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(codes.verifyPasswordResetCode(user, "123456")).thenReturn(true);
        when(passwords.encode("new-password")).thenReturn("encoded");
        when(refreshTokens.findAllByUserIdAndRevokedAtIsNull(user.getId())).thenReturn(List.of(first, second));

        var response = service.resetPassword(
                new ResetPasswordRequest("owner@example.com", "123456", "new-password"));

        assertThat(response.status()).isEqualTo("ok");
        verify(user).updatePasswordHash("encoded");
        verify(codes).deletePasswordResetCode(user);
        verify(first).revoke();
        verify(second).revoke();
    }
}
