package com.sauti.auth.application;

import com.sauti.auth.application.AuthDtos.MessageResponse;
import com.sauti.auth.application.port.AuthMailer;
import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.OneTimeCodePurpose;
import com.sauti.auth.domain.PasswordPolicy;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Forgot password": an emailed code lets the person choose a new password and ends every session. */
@Service
public class PasswordRecovery {
    static final String GENERIC_REQUEST = "If the account exists, password reset instructions have been sent.";
    static final String INVALID_CODE = "Invalid or expired reset code";

    private final UserRepository users;
    private final OneTimeCodes codes;
    private final AuthMailer mailer;
    private final PasswordHasher passwords;
    private final SessionService sessions;
    private final AccountQueries accounts;
    private final boolean exposeDevCodes;
    private final org.springframework.context.ApplicationEventPublisher events;

    public PasswordRecovery(UserRepository users, OneTimeCodes codes, AuthMailer mailer, PasswordHasher passwords,
                            SessionService sessions, AccountQueries accounts,
                            org.springframework.context.ApplicationEventPublisher events,
                            @Value("${sauti.auth.expose-dev-tokens:true}") boolean exposeDevCodes) {
        this.users = users;
        this.codes = codes;
        this.mailer = mailer;
        this.passwords = passwords;
        this.sessions = sessions;
        this.accounts = accounts;
        this.exposeDevCodes = exposeDevCodes;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public MessageResponse requestReset(String email) {
        var normalized = User.normalizeEmail(email);
        var user = users.findByEmail(normalized).orElse(null);
        if (user == null) return new MessageResponse("ok", GENERIC_REQUEST, null);
        var code = codes.issue(OneTimeCodePurpose.PASSWORD_RESET, normalized);
        mailer.sendPasswordResetCode(normalized, accounts.displayName(user), code);
        return new MessageResponse("ok", GENERIC_REQUEST, exposeDevCodes ? code : null);
    }

    /**
     * The new password is checked before the code, so a rejected password does not use up a guess.
     * A successful reset also proves the email, so it marks the account verified.
     */
    @Transactional
    public MessageResponse reset(String email, String code, String newPassword) {
        var normalized = User.normalizeEmail(email);
        PasswordPolicy.validate(newPassword, normalized);
        var user = users.findByEmail(normalized).orElse(null);
        if (user == null || !codes.verify(OneTimeCodePurpose.PASSWORD_RESET, normalized, code)) {
            throw new IllegalArgumentException(INVALID_CODE);
        }
        user.changePasswordHash(passwords.hash(newPassword));
        user.verifyEmail();
        users.save(user);
        codes.discard(OneTimeCodePurpose.PASSWORD_RESET, normalized);
        events.publishEvent(new AccountActivity(user.getId(), "password.reset", "Reset the password with an emailed code"));
        sessions.endAll(user.getId());
        return new MessageResponse("ok", "Password has been reset.", null);
    }
}
