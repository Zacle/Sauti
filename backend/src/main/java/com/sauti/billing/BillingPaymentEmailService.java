package com.sauti.billing;

import jakarta.mail.MessagingException;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class BillingPaymentEmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromEmail;
    private final String fromName;
    private final String replyToEmail;
    private final String dashboardBaseUrl;

    public BillingPaymentEmailService(JavaMailSender mailSender, TemplateEngine templateEngine,
            @Value("${sauti.email.from:noreply@sauti.local}") String fromEmail,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.email.reply-to:support@sauti.local}") String replyToEmail,
            @Value("${sauti.dashboard.base-url:https://sauti.uk}") String dashboardBaseUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.replyToEmail = replyToEmail;
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
    }

    public void send(BillingPaymentNotification notification) {
        var context = new Context();
        context.setVariable("businessName", notification.getBusinessName());
        context.setVariable("purchaseDescription", notification.getPurchaseDescription());
        context.setVariable("amount", notification.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        context.setVariable("currency", notification.getCurrency());
        context.setVariable("paidAt", notification.getPaidAt() == null ? "Confirmed by Whop"
                : notification.getPaidAt().format(DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm O")));
        context.setVariable("paymentReference", notification.getProviderPaymentId());
        context.setVariable("cardLast4", notification.getCardLast4());
        context.setVariable("testMode", notification.isTestMode());
        context.setVariable("billingUrl", dashboardBaseUrl + "/billing");
        var html = templateEngine.process("email/payment-confirmation", context);
        sendEmail(notification.getRecipientEmail(),
                notification.isTestMode() ? "Sauti sandbox payment confirmed" : "Your Sauti payment is confirmed", html);
    }

    private void sendEmail(String to, String subject, String html) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setReplyTo(replyToEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Failed to send payment confirmation email", exception);
        }
    }
}
