package com.sauti.auth.infrastructure;

import com.sauti.auth.application.port.AuthMailer;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Account emails over SMTP. Sending happens on a small background pool so a slow mail server never
 * delays a response, and so response time does not reveal whether an account exists.
 */
@Service
public class AuthEmailService implements AuthMailer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthEmailService.class);
    private final ExecutorService delivery = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "auth-email");
        thread.setDaemon(true);
        return thread;
    });
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromEmail;
    private final String fromName;
    private final String replyToEmail;
    private final String dashboardBaseUrl;

    public AuthEmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.email.reply-to:support@sauti.local}") String replyToEmail,
            @Value("${sauti.dashboard.base-url:https://sauti.uk}") String dashboardBaseUrl
    ) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.replyToEmail = replyToEmail;
        this.dashboardBaseUrl = stripTrailingSlash(dashboardBaseUrl);
    }

    @Override
    public void sendVerificationCode(String toEmail, String businessName, String code) {
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

    @Override
    public void sendPasswordResetCode(String toEmail, String businessName, String code) {
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

    @Override
    public void sendWelcome(String toEmail, String businessName) {
        var context = new Context();
        context.setVariable("businessName", businessName);
        context.setVariable("onboardingUrl", dashboardBaseUrl + "/onboarding");
        context.setVariable("helpUrl", dashboardBaseUrl + "/help");
        var html = templateEngine.process("email/welcome", context);
        sendEmail(toEmail, "Welcome to Sauti", html);
    }

    @PreDestroy
    void shutdown() {
        delivery.shutdown();
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        delivery.execute(() -> deliver(to, subject, htmlContent));
    }

    private void deliver(String to, String subject, String htmlContent) {
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
            LOGGER.warn("Account email delivery failed subject={} exception={}", subject, exception.getClass().getSimpleName());
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://sauti.uk";
        return value.trim().replaceAll("/+$", "");
    }
}
