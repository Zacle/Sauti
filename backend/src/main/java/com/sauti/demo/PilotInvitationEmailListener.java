package com.sauti.demo;

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
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class PilotInvitationEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PilotInvitationEmailListener.class);
    private final JavaMailSender mailSender;
    private final TemplateEngine templates;
    private final String fromEmail;
    private final String fromName;
    private final String replyTo;
    private final String dashboardBaseUrl;
    private final PilotInvitationDeliveryService deliveries;

    public PilotInvitationEmailListener(JavaMailSender mailSender, TemplateEngine templates,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.email.reply-to:support@sauti.local}") String replyTo,
            @Value("${sauti.dashboard.base-url:https://sauti.uk}") String dashboardBaseUrl,
            PilotInvitationDeliveryService deliveries) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.replyTo = replyTo;
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
        this.deliveries = deliveries;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PilotInvitationIssued event) {
        try {
            var context = new Context();
            context.setVariable("invitation", event.invitation());
            context.setVariable("acceptUrl", dashboardBaseUrl + "/accept-invite#token=" + event.rawToken());
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setReplyTo(replyTo);
            helper.setTo(event.invitation().getEmail());
            helper.setSubject("Your Sauti pilot invitation");
            helper.setText(templates.process("email/pilot-invitation", context), true);
            mailSender.send(message);
            deliveries.recordSent(event.invitation().getId());
        } catch (MessagingException | MailException exception) {
            LOGGER.warn("Pilot invitation email failed exception={}", exception.getClass().getSimpleName());
            deliveries.recordFailure(event.invitation().getId(), exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Pilot invitation email failed exception={}", exception.getClass().getSimpleName());
            deliveries.recordFailure(event.invitation().getId(), exception);
        }
    }
}
