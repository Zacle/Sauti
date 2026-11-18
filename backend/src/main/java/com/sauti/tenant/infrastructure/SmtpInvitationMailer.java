package com.sauti.tenant.infrastructure;

import com.sauti.tenant.application.InvitationService.InvitationIssued;
import com.sauti.tenant.application.port.InvitationMailer;
import jakarta.mail.MessagingException;
import java.util.Locale;
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

/** Sends workspace invitations over SMTP once the invitation has been committed. */
@Component
public class SmtpInvitationMailer implements InvitationMailer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpInvitationMailer.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templates;
    private final String fromEmail;
    private final String fromName;
    private final String replyTo;

    public SmtpInvitationMailer(JavaMailSender mailSender, TemplateEngine templates,
                                @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
                                @Value("${sauti.email.from-name:Sauti}") String fromName,
                                @Value("${sauti.email.reply-to:support@sauti.local}") String replyTo) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.replyTo = replyTo;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onInvitationIssued(InvitationIssued event) {
        send(event.email());
    }

    @Override
    public void send(InvitationEmail email) {
        try {
            var context = new Context();
            context.setVariable("workspaceName", email.workspaceName());
            context.setVariable("inviterEmail", email.inviterEmail());
            context.setVariable("role", displayRole(email.role()));
            context.setVariable("acceptUrl", email.acceptUrl());
            context.setVariable("toEmail", email.toEmail());
            context.setVariable("expiresAt", email.expiresAt());
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setReplyTo(replyTo);
            helper.setTo(email.toEmail());
            helper.setSubject("You're invited to " + email.workspaceName() + " on Sauti");
            helper.setText(templates.process("email/workspace-invitation", context), true);
            mailSender.send(message);
        } catch (MessagingException | MailException | IllegalStateException exception) {
            LOGGER.warn("Workspace invitation email failed exception={}", exception.getClass().getSimpleName());
        }
    }

    private String displayRole(String role) {
        var lower = role.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
