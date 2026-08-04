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
public class DemoRequestRejectedEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoRequestRejectedEmailListener.class);
    private final JavaMailSender mailSender; private final TemplateEngine templates;
    private final String fromEmail; private final String fromName; private final String replyTo;
    public DemoRequestRejectedEmailListener(JavaMailSender mailSender, TemplateEngine templates,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.email.reply-to:support@sauti.local}") String replyTo) {
        this.mailSender = mailSender; this.templates = templates; this.fromEmail = fromEmail;
        this.fromName = fromName; this.replyTo = replyTo;
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(DemoRequestRejected event) {
        try {
            var context = new Context(); context.setVariable("request", event.request());
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">"); helper.setReplyTo(replyTo);
            helper.setTo(event.request().getEmail()); helper.setSubject("Update on your Sauti demo request");
            helper.setText(templates.process("email/demo-request-rejected", context), true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            LOGGER.warn("Demo rejection email failed exception={}", exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            LOGGER.warn("Demo rejection email failed exception={}", exception.getClass().getSimpleName());
        }
    }
}
