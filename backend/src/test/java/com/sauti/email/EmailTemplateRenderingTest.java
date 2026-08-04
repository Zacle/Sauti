package com.sauti.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplateRenderingTest {
    @Test
    void rendersVerificationAndPasswordResetFromTheSharedSecurityTemplate() {
        var verification = securityContext(false);
        verification.setVariable("eyebrow", "Account activation");
        verification.setVariable("heading", "Confirm your email address");
        verification.setVariable("previewText", "Your Sauti verification code expires in 15 minutes.");
        verification.setVariable("intro", "Use this one-time code to verify your email.");
        verification.setVariable("codeLabel", "Verification code");
        verification.setVariable("noticeTitle", "Only enter this code in Sauti");
        verification.setVariable("notice", "Ignore this email if you did not create this workspace.");

        var verificationHtml = templateEngine().process("email/auth-code", verification);

        assertThat(verificationHtml)
                .contains("Confirm your email address")
                .contains("Account activation")
                .contains("Verification code")
                .contains("482913")
                .contains("Expires in 15 minutes")
                .contains("Only enter this code in Sauti");

        var reset = securityContext(true);
        reset.setVariable("eyebrow", "Security request");
        reset.setVariable("heading", "Reset your password");
        reset.setVariable("previewText", "Your Sauti password reset code expires in 15 minutes.");
        reset.setVariable("intro", "Use this one-time code to choose a new password.");
        reset.setVariable("codeLabel", "Password reset code");
        reset.setVariable("noticeTitle", "Did not request this?");
        reset.setVariable("notice", "Your current password will remain unchanged.");

        var resetHtml = templateEngine().process("email/auth-code", reset);

        assertThat(resetHtml)
                .contains("Reset your password")
                .contains("Security request")
                .contains("Password reset code")
                .contains("Did not request this?")
                .contains("Your current password will remain unchanged.");
    }

    @Test
    void rendersPostCallEmailWithOutcomeTimeAndPrimaryActions() {
        var context = new Context();
        context.setVariable("businessName", "Hairy");
        context.setVariable("agentName", "Ailsa");
        context.setVariable("testCall", false);
        context.setVariable("outcome", "Completed");
        context.setVariable("summary", "Zachary rescheduled a men hairstyle appointment.");
        context.setVariable("callerPhone", "0115752441");
        context.setVariable("intent", "Reschedule booking");
        context.setVariable("sentiment", "Positive");
        context.setVariable("startedAt", "29 Jul 2026 at 10:42 AM · Africa/Cairo (UTC+03:00)");
        context.setVariable("duration", "2 minutes 14 seconds");
        context.setVariable("callUrl", "https://sauti.uk/calls?callId=test-call");
        context.setVariable("hasRecording", true);
        context.setVariable("recordingUrl", "https://recordings.example/call.mp3");

        var html = templateEngine().process("email/call-summary", context);

        assertThat(html)
                .contains("Call completed")
                .contains("Zachary rescheduled a men hairstyle appointment.")
                .contains("29 Jul 2026 at 10:42 AM")
                .contains("Africa/Cairo (UTC+03:00)")
                .contains("2 minutes 14 seconds")
                .contains("Review call in Sauti")
                .contains("Listen to recording")
                .contains("https://sauti.uk/calls?callId=test-call");
    }

    @Test
    void rendersProfessionalWelcomeEmailWithAFocusedOnboardingPath() {
        var context = new Context();
        context.setVariable("businessName", "Hairy");
        context.setVariable("onboardingUrl", "https://sauti.uk/onboarding");
        context.setVariable("helpUrl", "https://sauti.uk/help");

        var html = templateEngine().process("email/welcome", context);

        assertThat(html)
                .contains("Your Sauti workspace is ready")
                .contains("Welcome, <span>Hairy</span>")
                .contains("Create your agent")
                .contains("Connect a customer channel")
                .contains("Make a test call")
                .contains("Set up your first agent")
                .contains("https://sauti.uk/onboarding")
                .contains("https://sauti.uk/help");
    }

    @Test
    void rendersRequesterReceiptAndRejectionEmails() {
        var request = new com.sauti.demo.DemoRequest("Acme Clinic", "Amina", "owner@example.com", "KE",
                "+254700000000", "Healthcare", "under-100", "voice", "Answer calls", null);
        var received = new Context(); received.setVariable("request", request);
        var receivedHtml = templateEngine().process("email/demo-request-received", received);
        assertThat(receivedHtml).contains("We received your request").contains("Amina")
                .contains("No paid phone number or provider resource is created");

        request.reject("Current pilot capacity is full", java.time.OffsetDateTime.now());
        var rejected = new Context(); rejected.setVariable("request", request);
        var rejectedHtml = templateEngine().process("email/demo-request-rejected", rejected);
        assertThat(rejectedHtml).contains("cannot open this pilot right now")
                .contains("Current pilot capacity is full").contains("reply to this email");
    }

    private Context securityContext(boolean resetRequest) {
        var context = new Context();
        context.setVariable("businessName", "Hairy");
        context.setVariable("code", "482913");
        context.setVariable("resetRequest", resetRequest);
        return context;
    }

    private SpringTemplateEngine templateEngine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
