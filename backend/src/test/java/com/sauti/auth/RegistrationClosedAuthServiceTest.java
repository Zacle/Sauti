package com.sauti.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.auth.AuthDtos.RegisterRequest;
import com.sauti.tenant.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationClosedAuthServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService service = new AuthService(
            tenants,
            users,
            mock(RefreshTokenRepository.class),
            mock(AuthEmailService.class),
            mock(GoogleOAuthService.class),
            mock(VerificationCodeService.class),
            mock(PasswordEncoder.class),
            mock(JwtService.class),
            mock(ApplicationEventPublisher.class),
            30,
            30,
            false,
            false
    );

    @Test
    void rejectsPasswordRegistrationBeforeCreatingAWorkspace() {
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "Acme", "owner@example.com", "KE", "secure-password"
        ))).isInstanceOf(RegistrationClosedException.class);

        verify(tenants, never()).save(org.mockito.ArgumentMatchers.any());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void letsExistingGoogleUsersLogInButRejectsUnknownGoogleIdentities() {
        var profile = new GoogleOAuthService.GoogleProfile("new@example.com", "New Owner");
        when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginWithGoogleProfile(profile, "Acme", "KE"))
                .isInstanceOf(RegistrationClosedException.class);

        verify(tenants, never()).save(org.mockito.ArgumentMatchers.any());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
