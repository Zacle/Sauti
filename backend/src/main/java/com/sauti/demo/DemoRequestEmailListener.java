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
public class DemoRequestEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoRequestEmailListener.class);
    private final JavaMailSender mailSender;
    private final TemplateEngine templates;
    private final String fromEmail;
    private final String fromName;
    private final String notificationEmail;

    public DemoRequestEmailListener(
            JavaMailSender mailSender,
            TemplateEngine templates,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.demo-request.notification-email:${sauti.email.reply-to:support@sauti.local}}")
            String notificationEmail) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.notificationEmail = notificationEmail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyParties(DemoRequestReceived event) {
        sendOwner(event.request());
        sendRequester(event.request());
    }

    private void sendOwner(DemoRequest request) {
        try {
            var context = new Context();
            context.setVariable("request", request);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setReplyTo(request.getEmail());
            helper.setTo(notificationEmail);
            helper.setSubject("New Sauti demo request — " + request.getBusinessName());
            helper.setText(templates.process("email/demo-request", context), true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            LOGGER.warn("Demo request notification failed exception={}", exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            LOGGER.warn("Demo request notification failed exception={}", exception.getClass().getSimpleName());
        }
    }

    private void sendRequester(DemoRequest request) {
        try {
            var context = new Context();
            context.setVariable("request", request);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setTo(request.getEmail());
            helper.setSubject("We received your Sauti demo request");
            helper.setText(templates.process("email/demo-request-received", context), true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            LOGGER.warn("Demo request receipt failed exception={}", exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            LOGGER.warn("Demo request receipt failed exception={}", exception.getClass().getSimpleName());
        }
    }
}
