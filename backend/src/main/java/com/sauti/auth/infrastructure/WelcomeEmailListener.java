package com.sauti.auth.infrastructure;

import com.sauti.auth.application.WelcomeEmailRequested;
import com.sauti.auth.application.port.AuthMailer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the welcome email once the account activation has committed. */
@Component
public class WelcomeEmailListener {
    private final AuthMailer mailer;

    public WelcomeEmailListener(AuthMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void accountActivated(WelcomeEmailRequested event) {
        mailer.sendWelcome(event.toEmail(), event.displayName());
    }
}
