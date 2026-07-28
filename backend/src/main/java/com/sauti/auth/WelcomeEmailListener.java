package com.sauti.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends onboarding email after account activation commits without blocking the auth response. */
@Component
public class WelcomeEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomeEmailListener.class);

    private final AuthEmailService authEmailService;

    public WelcomeEmailListener(AuthEmailService authEmailService) {
        this.authEmailService = authEmailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void accountActivated(WelcomeEmailRequested event) {
        try {
            authEmailService.sendWelcomeEmail(event.toEmail(), event.businessName());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Welcome email delivery failed exception={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
