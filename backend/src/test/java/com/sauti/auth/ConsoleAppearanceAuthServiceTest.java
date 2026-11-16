package com.sauti.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.auth.AuthDtos.ConsoleAppearanceRequest;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class ConsoleAppearanceAuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService service = new AuthService(
            mock(TenantRepository.class), users, mock(RefreshTokenRepository.class), mock(AuthEmailService.class),
            mock(GoogleOAuthService.class), mock(VerificationCodeService.class), mock(PasswordEncoder.class),
            mock(JwtService.class), mock(ApplicationEventPublisher.class), new PlatformAdminPolicy(""),
            30, 30, false, false);

    private User user() {
        var user = new User(new Tenant("Clinic", "owner@example.com", "KE"), "owner@example.com", "hash");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void newUsersDefaultToLightForestTheme() {
        var user = user();

        var appearance = service.consoleAppearance(user.getId());

        assertThat(appearance.mode()).isEqualTo("light");
        assertThat(appearance.accent()).isEqualTo("green");
    }

    @Test
    void savesNormalisedAppearanceForTheSignedInUser() {
        var user = user();

        var appearance = service.configureConsoleAppearance(
                user.getId(), new ConsoleAppearanceRequest(" Dark ", "VIOLET"));

        assertThat(appearance.mode()).isEqualTo("dark");
        assertThat(appearance.accent()).isEqualTo("violet");
        assertThat(user.getConsoleThemeMode()).isEqualTo("dark");
    }

    @Test
    void rejectsUnknownModesAndAccents() {
        var user = user();

        assertThatThrownBy(() -> service.configureConsoleAppearance(
                user.getId(), new ConsoleAppearanceRequest("sepia", "green")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configureConsoleAppearance(
                user.getId(), new ConsoleAppearanceRequest("dark", "neon")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getConsoleThemeMode()).isEqualTo("light");
    }
}
