package com.sauti.reliability;

import jakarta.mail.MessagingException;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReliabilityAlertEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReliabilityAlertEmailListener.class);
    private final JavaMailSender mailSender;
    private final ReliabilityMonitoringService monitoring;
    private final String fromEmail;
    private final String fromName;
    private final String recipient;
    private final String adminUrl;

    public ReliabilityAlertEmailListener(
            JavaMailSender mailSender,
            ReliabilityMonitoringService monitoring,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.reliability.alerts.notification-email:${sauti.email.reply-to:support@sauti.local}}")
            String recipient,
            @Value("${sauti.reliability.alerts.admin-url:${sauti.dashboard.base-url:http://localhost:8088}/admin/analytics}")
            String adminUrl) {
        this.mailSender = mailSender;
        this.monitoring = monitoring;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.recipient = recipient;
        this.adminUrl = adminUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(ReliabilityAlertRequested event) {
        try {
            var recovery = event.recovery();
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setTo(recipient);
            helper.setSubject((recovery ? "Resolved: " : "Sauti reliability alert: ") + event.provider());
            helper.setText(body(event), true);
            mailSender.send(message);
            monitoring.markNotificationSent(event.incidentId(), recovery,
                    java.time.OffsetDateTime.now(ZoneOffset.UTC));
        } catch (MessagingException | MailException exception) {
            LOGGER.warn("Reliability alert email failed provider={} recovery={} exception={}",
                    event.provider(), event.recovery(), exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            LOGGER.warn("Reliability alert processing failed provider={} recovery={} exception={}",
                    event.provider(), event.recovery(), exception.getClass().getSimpleName());
        }
    }

    private String body(ReliabilityAlertRequested event) {
        var state = event.recovery() ? "resolved" : "requires attention";
        return """
                <!doctype html><html><body style="font-family:Arial,sans-serif;color:#10243a">
                <h2>Sauti reliability incident %s</h2>
                <p><strong>Signal:</strong> %s</p>
                <p><strong>Severity:</strong> %s</p>
                <p>%s</p>
                <p><a href="%s">Open platform analytics</a></p>
                <p style="color:#60758a;font-size:12px">Observed at %s. This alert uses stored Sauti evidence and does not make a billable provider request.</p>
                </body></html>
                """.formatted(state, escape(event.provider()), escape(event.severity()),
                escape(event.summary()), escape(adminUrl), event.occurredAt());
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
