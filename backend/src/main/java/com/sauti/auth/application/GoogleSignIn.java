package com.sauti.auth.application;

import com.sauti.auth.RegistrationClosedException;
import com.sauti.auth.application.port.GoogleIdentityProvider.GoogleProfile;
import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.domain.OneTimeCodePurpose;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserIdentity;
import com.sauti.auth.domain.UserIdentityRepository;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.application.WorkspaceService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signing in with Google. Accounts are found by Google's permanent account id. The first time an
 * existing account signs in with Google it is linked by its verified email, once; after that the
 * email is never used to match again.
 */
@Service
public class GoogleSignIn {
    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final OneTimeCodes codes;
    private final WorkspaceService workspaces;
    private final PlatformAdminPolicy platformAdmins;
    private final ApplicationEventPublisher events;
    private final boolean publicRegistrationEnabled;
    private final Clock clock;

    @Autowired
    public GoogleSignIn(UserRepository users, UserIdentityRepository identities, OneTimeCodes codes,
                        WorkspaceService workspaces, PlatformAdminPolicy platformAdmins, ApplicationEventPublisher events,
                        @Value("${sauti.auth.public-registration-enabled:false}") boolean publicRegistrationEnabled) {
        this(users, identities, codes, workspaces, platformAdmins, events, publicRegistrationEnabled, Clock.systemUTC());
    }

    GoogleSignIn(UserRepository users, UserIdentityRepository identities, OneTimeCodes codes, WorkspaceService workspaces,
                 PlatformAdminPolicy platformAdmins, ApplicationEventPublisher events, boolean publicRegistrationEnabled,
                 Clock clock) {
        this.users = users;
        this.identities = identities;
        this.codes = codes;
        this.workspaces = workspaces;
        this.platformAdmins = platformAdmins;
        this.events = events;
        this.publicRegistrationEnabled = publicRegistrationEnabled;
        this.clock = clock;
    }

    @Transactional
    public User signIn(GoogleProfile profile, String businessName, String countryCode) {
        var linked = identities.findByProviderAndProviderSubject(UserIdentity.GOOGLE, profile.subject());
        if (linked.isPresent()) {
            return users.findById(linked.get().userId())
                    .orElseThrow(() -> new IllegalStateException("Linked account no longer exists"));
        }
        var email = User.normalizeEmail(profile.email());
        var existing = users.findByEmail(email);
        if (existing.isPresent()) return linkExisting(existing.get(), profile);
        return createAccount(profile, email, businessName, countryCode);
    }

    private User linkExisting(User user, GoogleProfile profile) {
        identities.save(new UserIdentity(user.getId(), UserIdentity.GOOGLE, profile.subject(), now()));
        if (!user.isEmailVerified()) {
            user.verifyEmail();
            codes.discard(OneTimeCodePurpose.EMAIL_VERIFICATION, user.getEmail());
            users.save(user);
            events.publishEvent(new WelcomeEmailRequested(user.getEmail(), displayName(profile)));
        }
        return user;
    }

    /**
     * New accounts get a workspace only when a country was chosen. Without one the person lands
     * on "Create a workspace" instead of Sauti guessing a country.
     */
    private User createAccount(GoogleProfile profile, String email, String businessName, String countryCode) {
        if (!publicRegistrationEnabled && !platformAdmins.allows(email)) throw new RegistrationClosedException();
        var user = users.save(User.verifiedWithoutPassword(email));
        identities.save(new UserIdentity(user.getId(), UserIdentity.GOOGLE, profile.subject(), now()));
        var name = businessName == null || businessName.isBlank() ? displayName(profile) : businessName.trim();
        if (countryCode != null && !countryCode.isBlank()) {
            var workspace = workspaces.create(user.getId(), email, name, countryCode);
            user.rememberWorkspace(workspace.workspace().id());
            users.save(user);
        }
        events.publishEvent(new WelcomeEmailRequested(email, name));
        return user;
    }

    private String displayName(GoogleProfile profile) {
        if (profile.name() != null && !profile.name().isBlank()) return profile.name().trim();
        return profile.email().split("@", 2)[0];
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
