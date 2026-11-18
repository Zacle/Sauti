package com.sauti.auth.application;

import com.sauti.auth.application.port.AccessTokens;
import com.sauti.auth.application.port.SecretSealer;
import com.sauti.auth.domain.PlatformAdmin;
import com.sauti.auth.domain.PlatformAdminRepository;
import com.sauti.auth.domain.Totp;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signing in to Sauti administration takes two steps: the account password, then a code from an
 * authenticator app. The first step returns a five-minute challenge; only the second step starts a
 * session, and only that kind of session carries platform-admin rights.
 *
 * <p>The configured admin email list only bootstraps: an allowlisted account that is not yet an
 * admin becomes one at its first admin sign-in and must then register an authenticator.
 */
@Service
public class AdminSignIn {
    static final String NOT_AN_ADMIN = "This account cannot use Sauti administration";
    static final String INVALID_CODE = "That code didn't match. Use the current code from your authenticator app.";

    private final PasswordSignIn passwordSignIn;
    private final UserRepository users;
    private final PlatformAdminRepository admins;
    private final PlatformAdminPolicy bootstrap;
    private final AccessTokens tokens;
    private final SecretSealer sealer;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AdminSignIn(PasswordSignIn passwordSignIn, UserRepository users, PlatformAdminRepository admins,
                       PlatformAdminPolicy bootstrap, AccessTokens tokens, SecretSealer sealer,
                       ApplicationEventPublisher events) {
        this(passwordSignIn, users, admins, bootstrap, tokens, sealer, events, Clock.systemUTC());
    }

    AdminSignIn(PasswordSignIn passwordSignIn, UserRepository users, PlatformAdminRepository admins,
                PlatformAdminPolicy bootstrap, AccessTokens tokens, SecretSealer sealer,
                ApplicationEventPublisher events, Clock clock) {
        this.passwordSignIn = passwordSignIn;
        this.users = users;
        this.admins = admins;
        this.bootstrap = bootstrap;
        this.tokens = tokens;
        this.sealer = sealer;
        this.events = events;
        this.clock = clock;
    }

    /** Step one. Wrong password and "not an admin" give different errors only after the password is right. */
    @Transactional
    public Challenge checkPassword(String email, String password) {
        var user = passwordSignIn.signIn(email, password);
        var admin = admins.findById(user.getId()).orElseGet(() -> {
            if (!bootstrap.allows(user.getEmail())) throw new AccessDeniedException(NOT_AN_ADMIN);
            return admins.save(new PlatformAdmin(user.getId(), now()));
        });
        return new Challenge(tokens.issueAdminChallenge(user.getId()), admin.hasAuthenticator());
    }

    /** For an admin without an authenticator: a new secret to add to their app. Replaces any unconfirmed one. */
    @Transactional
    public AuthenticatorSetup beginAuthenticatorSetup(String challenge) {
        var user = userFor(challenge);
        var admin = admins.findById(user.getId()).orElseThrow(() -> new AccessDeniedException(NOT_AN_ADMIN));
        if (admin.hasAuthenticator()) {
            throw new IllegalArgumentException("Your authenticator app is already set up. Enter its current code.");
        }
        var secret = Totp.newSecret(random);
        admin.beginAuthenticatorSetup(sealer.seal(secret));
        admins.save(admin);
        return new AuthenticatorSetup(secret, Totp.otpauthUri("Sauti Admin", user.getEmail(), secret));
    }

    /**
     * Step two. A correct code confirms a new authenticator on first use. Returns the account so
     * the caller can start an elevated session.
     */
    @Transactional
    public User verifyCode(String challenge, String code) {
        var user = userFor(challenge);
        var admin = admins.findById(user.getId()).orElseThrow(() -> new AccessDeniedException(NOT_AN_ADMIN));
        if (admin.totpSecretEncrypted() == null) throw new IllegalArgumentException("Set up your authenticator app first");
        if (!Totp.verify(sealer.open(admin.totpSecretEncrypted()), code, clock.instant())) {
            events.publishEvent(new AccountActivity(user.getId(), "admin.code_rejected", "Entered a wrong administration code"));
            throw new IllegalArgumentException(INVALID_CODE);
        }
        if (!admin.hasAuthenticator()) {
            admin.confirmAuthenticator(now());
            admins.save(admin);
            events.publishEvent(new AccountActivity(user.getId(), "admin.authenticator_added", "Set up an authenticator app for administration"));
        }
        return user;
    }

    public UUID challengeUser(String challenge) {
        return userFor(challenge).getId();
    }

    private User userFor(String challenge) {
        UUID userId;
        try {
            userId = tokens.verifyAdminChallenge(challenge);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Your sign-in step expired. Start again with your password.");
        }
        return users.findById(userId).orElseThrow(() -> new AccessDeniedException(NOT_AN_ADMIN));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** {@code authenticatorReady} false means the next step is setup, not a code. */
    public record Challenge(String challenge, boolean authenticatorReady) {
    }

    /** Shown once during setup: the key to type into the app, and a link that opens it. */
    public record AuthenticatorSetup(String secret, String otpauthUri) {
    }
}
