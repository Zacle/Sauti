package com.sauti.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.auth.RegistrationClosedException;
import com.sauti.auth.application.AuthDtos.RegisterRequest;
import com.sauti.auth.application.port.AuthMailer;
import com.sauti.auth.application.port.GoogleIdentityProvider.GoogleProfile;
import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserIdentity;
import com.sauti.auth.domain.UserIdentityRepository;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.application.InvitationService;
import com.sauti.tenant.application.WorkspaceService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AccountCreationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserIdentityRepository identities = mock(UserIdentityRepository.class);
    private final WorkspaceService workspaces = mock(WorkspaceService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @Test
    void publicRegistrationIsRejectedBeforeAnythingIsCreatedWhileClosed() {
        var registration = new AccountRegistration(users, mock(PasswordHasher.class), mock(OneTimeCodes.class),
                mock(AuthMailer.class), workspaces, mock(InvitationService.class), events, false, false);

        assertThatThrownBy(() -> registration.register(new RegisterRequest("Acme", "owner@example.com", "KE", "river-mango-42")))
                .isInstanceOf(RegistrationClosedException.class);
        verify(users, never()).save(any());
        verify(workspaces, never()).create(any(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownGoogleAccountsCannotSignUpWhileRegistrationIsClosed() {
        var google = googleSignIn("");
        when(identities.findByProviderAndProviderSubject(UserIdentity.GOOGLE, "google-sub-1")).thenReturn(Optional.empty());
        when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> google.signIn(new GoogleProfile("google-sub-1", "new@example.com", "New"), "Acme", "KE"))
                .isInstanceOf(RegistrationClosedException.class);
        verify(users, never()).save(any());
    }

    @Test
    void aLinkedGoogleAccountIsFoundByItsGoogleIdEvenIfTheEmailChanged() {
        var google = googleSignIn("");
        var user = User.verifiedWithoutPassword("old@example.com");
        when(identities.findByProviderAndProviderSubject(UserIdentity.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(new UserIdentity(user.getId(), UserIdentity.GOOGLE, "google-sub-1", OffsetDateTime.now())));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        var signedIn = google.signIn(new GoogleProfile("google-sub-1", "renamed@example.com", "Owner"), null, null);

        assertThat(signedIn).isSameAs(user);
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void anExistingAccountIsLinkedOnceOnItsFirstGoogleSignIn() {
        var google = googleSignIn("");
        var user = User.withPassword("owner@example.com", "hash");
        when(identities.findByProviderAndProviderSubject(UserIdentity.GOOGLE, "google-sub-2")).thenReturn(Optional.empty());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));

        var signedIn = google.signIn(new GoogleProfile("google-sub-2", "owner@example.com", "Owner"), null, null);

        assertThat(signedIn).isSameAs(user);
        assertThat(user.isEmailVerified()).isTrue();
        verify(identities).save(any(UserIdentity.class));
    }

    @Test
    void anAllowlistedAdminWithoutACountryGetsAnAccountButNoGuessedWorkspace() {
        var google = googleSignIn("support@sauti.uk");
        when(identities.findByProviderAndProviderSubject(UserIdentity.GOOGLE, "google-sub-3")).thenReturn(Optional.empty());
        when(users.findByEmail("support@sauti.uk")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var signedIn = google.signIn(new GoogleProfile("google-sub-3", "support@sauti.uk", "Sauti Support"), null, null);

        assertThat(signedIn.getEmail()).isEqualTo("support@sauti.uk");
        assertThat(signedIn.hasPassword()).isFalse();
        verify(workspaces, never()).create(any(), anyString(), anyString(), anyString());
    }

    private GoogleSignIn googleSignIn(String adminEmails) {
        return new GoogleSignIn(users, identities, mock(OneTimeCodes.class), workspaces,
                new PlatformAdminPolicy(adminEmails), events, false, Clock.systemUTC());
    }
}
