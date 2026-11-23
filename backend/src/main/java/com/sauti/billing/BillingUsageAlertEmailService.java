package com.sauti.billing;

import jakarta.mail.MessagingException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class BillingUsageAlertEmailService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM uuuu");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromEmail;
    private final String fromName;
    private final String replyToEmail;
    private final String dashboardBaseUrl;

    public BillingUsageAlertEmailService(JavaMailSender mailSender, TemplateEngine templateEngine,
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

    public void send(BillingUsageAlert alert) {
        var content = content(alert);
        var context = new Context();
        context.setVariable("preheader", content.subject());
        context.setVariable("heading", content.heading());
        context.setVariable("body", content.body());
        context.setVariable("exhausted", alert.getThresholdPercent() >= 100);
        context.setVariable("usedLabel", alert.messages() ? "Messages sent" : "Minutes used");
        context.setVariable("used", alert.getUsedAmount());
        context.setVariable("included", alert.getIncludedAmount());
        context.setVariable("percent", Math.min(100, Math.round(alert.getUsedAmount() * 100f / alert.getIncludedAmount())));
        context.setVariable("workspace", alert.getBusinessName());
        context.setVariable("renewsLabel", alert.messages() ? "Messages renew" : "Minutes renew");
        context.setVariable("renews", alert.isTrial() ? (alert.messages() ? "Trial messages don't renew" : "Trial minutes don't renew") : date(alert));
        context.setVariable("billingUrl", dashboardBaseUrl + "/billing?tab=plans");
        context.setVariable("buttonLabel", alert.isTrial() ? "Choose a plan" : "View plans");
        var html = templateEngine.process("email/usage-alert", context);
        sendEmail(alert.getRecipientEmail(), content.subject(), html);
    }

    record Content(String subject, String heading, String body) { }

    static Content content(BillingUsageAlert alert) {
        return alert.messages() ? messageContent(alert) : minuteContent(alert);
    }

    private static Content minuteContent(BillingUsageAlert alert) {
        var allowance = alert.isTrial() ? "trial minutes" : "call minutes";
        if (alert.getThresholdPercent() >= 100) {
            var body = alert.isTrial()
                    ? "Your agents have used all " + alert.getIncludedAmount() + " trial minutes for "
                            + alert.getBusinessName() + ", so new AI calls are paused. Calls already in progress "
                            + "finished normally. Choose a plan to keep calling."
                    : "Your agents have used all " + alert.getIncludedAmount() + " minutes in this billing cycle for "
                            + alert.getBusinessName() + ", so new AI calls are paused until " + date(alert)
                            + ". Calls already in progress finished normally. To keep calling before then, "
                            + "move to a larger plan.";
            return new Content("Your Sauti " + allowance + " are used up", "New AI calls are paused", body);
        }
        var body = alert.isTrial()
                ? "Your agents have used " + alert.getUsedAmount() + " of the " + alert.getIncludedAmount()
                        + " trial minutes for " + alert.getBusinessName()
                        + ". When they run out, new AI calls pause. Choose a plan to keep calling."
                : "Your agents have used " + alert.getUsedAmount() + " of the " + alert.getIncludedAmount()
                        + " minutes in this billing cycle for " + alert.getBusinessName()
                        + ". When they run out, new AI calls pause until " + date(alert)
                        + ". Nothing extra is charged.";
        return new Content("You've used " + alert.getThresholdPercent() + "% of your Sauti " + allowance,
                alert.getThresholdPercent() + "% of your " + allowance + " used", body);
    }

    private static Content messageContent(BillingUsageAlert alert) {
        var allowance = alert.isTrial() ? "trial messages" : "messages";
        var paused = "AI replies on WhatsApp, text messages from your agents and automatic confirmations";
        if (alert.getThresholdPercent() >= 100) {
            var body = "Sauti has sent all " + alert.getIncludedAmount() + " "
                    + (alert.isTrial() ? "trial messages" : "messages in this billing cycle") + " for "
                    + alert.getBusinessName() + ", so " + paused + " are paused"
                    + (alert.isTrial() ? "" : " until " + date(alert)) + ". Customer messages still reach your "
                    + "inbox, and your team can reply there. Calls are not affected. "
                    + (alert.isTrial() ? "Choose a plan to turn them back on." : "To resume them sooner, move to a larger plan.");
            return new Content("Your Sauti " + allowance + " are used up", "Automatic messages are paused", body);
        }
        var body = "Sauti has sent " + alert.getUsedAmount() + " of the " + alert.getIncludedAmount() + " "
                + (alert.isTrial() ? "trial messages" : "messages in this billing cycle") + " for "
                + alert.getBusinessName() + ". When they run out, " + paused + " pause"
                + (alert.isTrial() ? "." : " until " + date(alert) + ". Nothing extra is charged.")
                + " Your team can still reply from the inbox.";
        return new Content("You've used " + alert.getThresholdPercent() + "% of your Sauti " + allowance,
                alert.getThresholdPercent() + "% of your " + allowance + " used", body);
    }

    private static String date(BillingUsageAlert alert) {
        return alert.getCycleEndsAt() == null ? "the next cycle"
                : alert.getCycleEndsAt().withOffsetSameInstant(ZoneOffset.UTC).format(DATE);
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
            throw new IllegalStateException("Failed to send usage alert email", exception);
        }
    }
}
