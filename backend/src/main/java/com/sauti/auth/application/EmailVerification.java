package com.sauti.auth.application;

import com.sauti.auth.application.AuthDtos.MessageResponse;
import com.sauti.auth.application.port.AuthMailer;
import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.domain.OneTimeCodePurpose;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirming that a person owns their email. A valid code is always required, including for an
 * already-verified account, so this endpoint can never be used to sign in without proof.
 */
@Service
public class EmailVerification {
    static final String INVALID_CODE = "Invalid or expired verification code";
    static final String GENERIC_RESEND = "If verification is needed, a new email has been sent.";

    private final UserRepository users;
    private final OneTimeCodes codes;
    private final AuthMailer mailer;
    private final AccountQueries accounts;
    private final ApplicationEventPublisher events;
    private final boolean exposeDevCodes;

    public EmailVerification(UserRepository users, OneTimeCodes codes, AuthMailer mailer, AccountQueries accounts,
                             ApplicationEventPublisher events,
                             @Value("${sauti.auth.expose-dev-tokens:true}") boolean exposeDevCodes) {
        this.users = users;
        this.codes = codes;
        this.mailer = mailer;
        this.accounts = accounts;
        this.events = events;
        this.exposeDevCodes = exposeDevCodes;
    }

    @Transactional
    public User verify(String email, String code) {
        var normalized = User.normalizeEmail(email);
        var user = users.findByEmail(normalized).orElseThrow(() -> new IllegalArgumentException(INVALID_CODE));
        if (!codes.verify(OneTimeCodePurpose.EMAIL_VERIFICATION, normalized, code)) {
            throw new IllegalArgumentException(INVALID_CODE);
        }
        codes.discard(OneTimeCodePurpose.EMAIL_VERIFICATION, normalized);
        if (!user.isEmailVerified()) {
            user.verifyEmail();
            users.save(user);
            events.publishEvent(new WelcomeEmailRequested(user.getEmail(), accounts.displayName(user)));
        }
        return user;
    }

    /** Same answer whether or not the account exists or is already verified. */
    @Transactional(readOnly = true)
    public MessageResponse resend(String email) {
        var normalized = User.normalizeEmail(email);
        var user = users.findByEmail(normalized).filter(found -> !found.isEmailVerified()).orElse(null);
        if (user == null) return new MessageResponse("ok", GENERIC_RESEND, null);
        var code = codes.issue(OneTimeCodePurpose.EMAIL_VERIFICATION, normalized);
        mailer.sendVerificationCode(normalized, accounts.displayName(user), code);
        return new MessageResponse("ok", GENERIC_RESEND, exposeDevCodes ? code : null);
    }
}
