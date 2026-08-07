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
            new PlatformAdminPolicy("support@sauti.uk"),
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

    @Test
    void letsAnAllowlistedPlatformAdministratorCreateTheirGoogleBackedAccount() {
        var tenant = new com.sauti.tenant.Tenant("Sauti Support", "support@sauti.uk", "GB");
        var user = new User(tenant, "support@sauti.uk", "generated");
        var refreshTokens = mock(RefreshTokenRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var jwt = mock(JwtService.class);
        when(users.findByEmail("support@sauti.uk")).thenReturn(Optional.empty());
        when(tenants.save(org.mockito.ArgumentMatchers.any())).thenReturn(tenant);
        when(passwords.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("generated");
        when(users.save(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(jwt.issueAccessToken(user)).thenReturn("access");
        when(jwt.roleFor(user)).thenReturn(PlatformAdminPolicy.ROLE);
        var adminService = new AuthService(tenants, users, refreshTokens, mock(AuthEmailService.class),
                mock(GoogleOAuthService.class), mock(VerificationCodeService.class), passwords, jwt,
                mock(ApplicationEventPublisher.class), new PlatformAdminPolicy("support@sauti.uk"),
                30, 30, false, false);

        var response = adminService.loginWithGoogleProfile(
                new GoogleOAuthService.GoogleProfile("support@sauti.uk", "Sauti Support"), null, "GB");

        org.assertj.core.api.Assertions.assertThat(response.role()).isEqualTo(PlatformAdminPolicy.ROLE);
        verify(tenants).save(org.mockito.ArgumentMatchers.any());
        verify(users).save(org.mockito.ArgumentMatchers.any());
    }
}
