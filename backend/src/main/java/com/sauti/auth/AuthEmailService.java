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
        context.setVariable("eyebrow", "Account activation");
        context.setVariable("heading", "Confirm your email address");
        context.setVariable("previewText", "Your Sauti verification code expires in 15 minutes.");
        context.setVariable("intro", "Use this one-time code to verify your email and activate your Sauti workspace.");
        context.setVariable("codeLabel", "Verification code");
        context.setVariable("noticeTitle", "Only enter this code in Sauti");
        context.setVariable("notice", "If you did not create this workspace, you can safely ignore this email.");
        context.setVariable("resetRequest", false);
        var html = templateEngine.process("email/auth-code", context);
        sendEmail(toEmail, "Verify your Sauti account", html);
    }

    public void sendPasswordResetEmail(String toEmail, String businessName, String code) {
        var context = new Context();
        context.setVariable("businessName", businessName);
        context.setVariable("code", code);
        context.setVariable("eyebrow", "Security request");
        context.setVariable("heading", "Reset your password");
        context.setVariable("previewText", "Your Sauti password reset code expires in 15 minutes.");
        context.setVariable("intro", "Use this one-time code to continue to Sauti and choose a new password.");
        context.setVariable("codeLabel", "Password reset code");
        context.setVariable("noticeTitle", "Did not request this?");
        context.setVariable("notice", "Ignore this email. Your current password will remain unchanged.");
        context.setVariable("resetRequest", true);
        var html = templateEngine.process("email/auth-code", context);
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
