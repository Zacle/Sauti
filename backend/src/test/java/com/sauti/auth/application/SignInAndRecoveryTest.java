package com.sauti.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.auth.UnverifiedEmailException;
import com.sauti.auth.application.port.AuthMailer;
import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.OneTimeCodePurpose;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SignInAndRecoveryTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final OneTimeCodes codes = mock(OneTimeCodes.class);
    private final AuthMailer mailer = mock(AuthMailer.class);
    private final SessionService sessions = mock(SessionService.class);
    private final AccountQueries accounts = mock(AccountQueries.class);

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameErrorAfterTheSameWork() {
        var signIn = new PasswordSignIn(users, passwords);
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> signIn.signIn("Nobody@Example.com", "river-mango-42"))
                .hasMessage(PasswordSignIn.INVALID_CREDENTIALS);
        verify(passwords).spendEquivalentTime("river-mango-42");

        var user = verified("owner@example.com");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("wrong-password", user.getPasswordHash())).thenReturn(false);
        assertThatThrownBy(() -> signIn.signIn("owner@example.com", "wrong-password"))
                .hasMessage(PasswordSignIn.INVALID_CREDENTIALS);
    }

    @Test
    void googleOnlyAccountsCannotSignInWithAPassword() {
        var signIn = new PasswordSignIn(users, passwords);
        when(users.findByEmail("google@example.com")).thenReturn(Optional.of(User.verifiedWithoutPassword("google@example.com")));

        assertThatThrownBy(() -> signIn.signIn("google@example.com", "anything-at-all"))
                .hasMessage(PasswordSignIn.INVALID_CREDENTIALS);
    }

    @Test
    void unverifiedAccountsAreToldToVerifyOnlyAfterTheCorrectPassword() {
        var signIn = new PasswordSignIn(users, passwords);
        var user = User.withPassword("new@example.com", "hash");
        when(users.findByEmail("new@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("river-mango-42", "hash")).thenReturn(true);

        assertThatThrownBy(() -> signIn.signIn("new@example.com", "river-mango-42"))
                .isInstanceOf(UnverifiedEmailException.class);
    }

    @Test
    void verifyingEmailAlwaysRequiresAValidCodeEvenForVerifiedAccounts() {
        var verification = new EmailVerification(users, codes, mailer, accounts, mock(ApplicationEventPublisher.class), false);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(verified("owner@example.com")));
        when(codes.verify(OneTimeCodePurpose.EMAIL_VERIFICATION, "owner@example.com", "000000")).thenReturn(false);

        assertThatThrownBy(() -> verification.verify("owner@example.com", "000000"))
                .hasMessage(EmailVerification.INVALID_CODE);
    }

    @Test
    void verifyingAnUnknownEmailLooksLikeAWrongCode() {
        var verification = new EmailVerification(users, codes, mailer, accounts, mock(ApplicationEventPublisher.class), false);
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verification.verify("nobody@example.com", "123456"))
                .hasMessage(EmailVerification.INVALID_CODE);
    }

    @Test
    void passwordResetChangesThePasswordAndEndsEverySession() {
        var recovery = new PasswordRecovery(users, codes, mailer, passwords, sessions, accounts, mock(ApplicationEventPublisher.class), false);
        var user = verified("owner@example.com");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(codes.verify(OneTimeCodePurpose.PASSWORD_RESET, "owner@example.com", "123456")).thenReturn(true);
        when(passwords.hash("river-mango-42")).thenReturn("new-hash");

        var response = recovery.reset("owner@example.com", "123456", "river-mango-42");

        assertThat(response.status()).isEqualTo("ok");
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(codes).discard(OneTimeCodePurpose.PASSWORD_RESET, "owner@example.com");
        verify(sessions).endAll(user.getId());
    }

    @Test
    void aWeakNewPasswordIsRejectedBeforeTheResetCodeIsChecked() {
        var recovery = new PasswordRecovery(users, codes, mailer, passwords, sessions, accounts, mock(ApplicationEventPublisher.class), false);

        assertThatThrownBy(() -> recovery.reset("owner@example.com", "123456", "password123"))
                .hasMessageContaining("too easy");
        verify(codes, never()).verify(any(), anyString(), anyString());
    }

    @Test
    void resetRequestsForUnknownEmailsSendNothingAndLookTheSame() {
        var recovery = new PasswordRecovery(users, codes, mailer, passwords, sessions, accounts, mock(ApplicationEventPublisher.class), false);
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        var response = recovery.requestReset("nobody@example.com");

        assertThat(response.message()).isEqualTo(PasswordRecovery.GENERIC_REQUEST);
        verify(mailer, never()).sendPasswordResetCode(anyString(), anyString(), anyString());
    }

    @Test
    void changingPasswordChecksTheCurrentOneAndEndsEverySession() {
        var settings = new AccountSettings(users, passwords, sessions, mock(com.sauti.tenant.application.WorkspaceService.class), mock(ApplicationEventPublisher.class));
        var user = verified("owner@example.com");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwords.matches("current-secret-1", "hash")).thenReturn(true);
        when(passwords.matches("river-mango-42", "hash")).thenReturn(false);
        when(passwords.hash("river-mango-42")).thenReturn("new-hash");

        assertThatThrownBy(() -> settings.changePassword(user.getId(), "wrong", "river-mango-42"))
                .hasMessage("Current password is incorrect");

        settings.changePassword(user.getId(), "current-secret-1", "river-mango-42");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(sessions).endAll(eq(user.getId()));
    }

    @Test
    void consoleAppearanceIsValidatedAndSaved() {
        var settings = new AccountSettings(users, passwords, sessions, mock(com.sauti.tenant.application.WorkspaceService.class), mock(ApplicationEventPublisher.class));
        var user = verified("owner@example.com");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        var saved = settings.configureAppearance(user.getId(), new AuthDtos.ConsoleAppearanceRequest("Dark", "BLUE"));

        assertThat(saved.mode()).isEqualTo("dark");
        assertThat(saved.accent()).isEqualTo("blue");
        assertThatThrownBy(() -> settings.configureAppearance(user.getId(), new AuthDtos.ConsoleAppearanceRequest("neon", "blue")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User verified(String email) {
        var user = User.withPassword(email, "hash");
        user.verifyEmail();
        return user;
    }
}
