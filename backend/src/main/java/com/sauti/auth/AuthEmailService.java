package com.sauti.auth;

import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class AuthEmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromEmail;
    private final String fromName;
    private final String replyToEmail;

    public AuthEmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.email.reply-to:support@sauti.local}") String replyToEmail
    ) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.replyToEmail = replyToEmail;
    }

    public void sendVerificationEmail(String toEmail, String businessName, String code) {
        var context = new Context();
        context.setVariable("businessName", businessName);
        context.setVariable("code", code);
        var html = templateEngine.process("email/verification", context);
        sendEmail(toEmail, "Verify your Sauti account", html);
    }

    public void sendPasswordResetEmail(String toEmail, String businessName, String code) {
        var context = new Context();
        context.setVariable("businessName", businessName);
        context.setVariable("code", code);
        var html = templateEngine.process("email/reset-password", context);
        sendEmail(toEmail, "Reset your Sauti password", html);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setReplyTo(replyToEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Failed to send auth email to " + to, exception);
        }
    }
}
