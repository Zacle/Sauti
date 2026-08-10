package com.sauti.billing;

import jakarta.mail.MessagingException;
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
public class BillingPlanChangeEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(BillingPlanChangeEmailListener.class);
    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String fromName;
    private final String supportEmail;

    public BillingPlanChangeEmailListener(JavaMailSender mailSender,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.email.reply-to:support@sauti.uk}") String supportEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.supportEmail = supportEmail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(BillingPlanChangeRequested event) {
        sendSupport(event);
        sendOwner(event);
    }

    private void sendSupport(BillingPlanChangeRequested event) {
        var request = event.request();
        send(supportEmail, event.ownerEmail(), "Plan change requested — " + event.businessName(),
                "<p><strong>" + escaped(event.businessName()) + "</strong> requested a Sauti plan change.</p>"
                + "<p>Current: " + escaped(request.getCurrentPlan()) + "<br>Target: "
                + escaped(request.getTargetPlan()) + " (" + escaped(request.getTargetInterval()) + ")<br>"
                + "Whop membership: " + escaped(request.getProviderSubscriptionId()) + "</p>"
                + "<p>Verify the exact membership in Whop before making any billing change.</p>", "support");
    }

    private void sendOwner(BillingPlanChangeRequested event) {
        var request = event.request();
        send(event.ownerEmail(), supportEmail, "We received your Sauti plan change request",
                "<p>We received your request to change from <strong>" + escaped(request.getCurrentPlan())
                + "</strong> to <strong>" + escaped(request.getTargetPlan()) + "</strong> ("
                + escaped(request.getTargetInterval()) + ").</p>"
                + "<p>Your current subscription remains unchanged. We will confirm the effective date and any billing "
                + "difference before completing the change. You do not need to purchase another subscription.</p>", "owner");
    }

    private void send(String to, String replyTo, String subject, String html, String label) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setReplyTo(replyTo);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            LOGGER.warn("Billing plan change {} email failed exception={}", label,
                    exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            LOGGER.warn("Billing plan change {} email failed exception={}", label,
                    exception.getClass().getSimpleName());
        }
    }

    private static String escaped(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
