package com.sauti.auth.application;

import com.sauti.auth.RegistrationClosedException;
import com.sauti.auth.application.AuthDtos.RegisterRequest;
import com.sauti.auth.application.AuthDtos.RegisterResponse;
import com.sauti.auth.application.port.AuthMailer;
import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.OneTimeCodePurpose;
import com.sauti.auth.domain.PasswordPolicy;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.application.InvitationService;
import com.sauti.tenant.application.WorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating accounts: public sign-up (behind a flag), pilot invitations, and workspace invitations. */
@Service
public class AccountRegistration {
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final OneTimeCodes codes;
    private final AuthMailer mailer;
    private final WorkspaceService workspaces;
    private final InvitationService invitations;
    private final ApplicationEventPublisher events;
    private final boolean exposeDevCodes;
    private final boolean publicRegistrationEnabled;

    public AccountRegistration(UserRepository users, PasswordHasher passwords, OneTimeCodes codes, AuthMailer mailer,
                               WorkspaceService workspaces, InvitationService invitations, ApplicationEventPublisher events,
                               @Value("${sauti.auth.expose-dev-tokens:true}") boolean exposeDevCodes,
                               @Value("${sauti.auth.public-registration-enabled:false}") boolean publicRegistrationEnabled) {
        this.users = users;
        this.passwords = passwords;
        this.codes = codes;
        this.mailer = mailer;
        this.workspaces = workspaces;
        this.invitations = invitations;
        this.events = events;
        this.exposeDevCodes = exposeDevCodes;
        this.publicRegistrationEnabled = publicRegistrationEnabled;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!publicRegistrationEnabled) throw new RegistrationClosedException();
        return createWithWorkspace(request.businessName(), request.email(), request.countryCode(), request.password());
    }

    /** Used by an accepted pilot invitation, whose details were already reviewed by Sauti. */
    @Transactional
    public RegisterResponse registerInvited(String businessName, String email, String countryCode, String password) {
        return createWithWorkspace(businessName, email, countryCode, password);
    }

    /**
     * Creates an account from a workspace invitation and adds it to that workspace. The email is
     * treated as verified because the invitation link was delivered to it.
     */
    @Transactional
    public User acceptInvitationAsNewAccount(String token, String password) {
        var preview = invitations.preview(token);
        if (preview.accountExists()) {
            throw new IllegalArgumentException("You already have a Sauti account. Sign in to accept this invitation.");
        }
        PasswordPolicy.validate(password, preview.email());
        var created = User.withPassword(preview.email(), passwords.hash(password));
        created.verifyEmail();
        var user = users.save(created);
        var joined = invitations.accept(token, user.getId(), user.getEmail());
        user.rememberWorkspace(joined.workspace().id());
        users.save(user);
        events.publishEvent(new WelcomeEmailRequested(user.getEmail(), joined.workspace().businessName()));
        return user;
    }

    private RegisterResponse createWithWorkspace(String businessName, String rawEmail, String countryCode, String password) {
        var email = User.normalizeEmail(rawEmail);
        PasswordPolicy.validate(password, email);
        if (users.existsByEmail(email)) throw new IllegalArgumentException("Email is already registered");
        var user = users.save(User.withPassword(email, passwords.hash(password)));
        var workspace = workspaces.create(user.getId(), email, businessName, countryCode);
        user.rememberWorkspace(workspace.workspace().id());
        users.save(user);
        var code = codes.issue(OneTimeCodePurpose.EMAIL_VERIFICATION, email);
        mailer.sendVerificationCode(email, workspace.workspace().businessName(), code);
        return new RegisterResponse(
                "verification_required",
                "Registration successful. Verify your email before logging in.",
                exposeDevCodes ? code : null,
                workspace.workspace());
    }
}
