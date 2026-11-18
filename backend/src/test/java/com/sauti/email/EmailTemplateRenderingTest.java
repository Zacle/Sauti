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
        save("verification-code", verificationHtml);

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
        save("call-summary", html);

        assertThat(html)
                .contains("Call completed")
                .contains("Zachary rescheduled a men hairstyle appointment.")
                .contains("29 Jul 2026 at 10:42 AM")
                .contains("Africa/Cairo (UTC+03:00)")
                .contains("2 minutes 14 seconds")
                .contains("Review call in Sauti")
                .contains("Listen to the recording")
                .contains("https://sauti.uk/calls?callId=test-call");
    }

    @Test
    void rendersProfessionalWelcomeEmailWithAFocusedOnboardingPath() {
        var context = new Context();
        context.setVariable("businessName", "Hairy");
        context.setVariable("onboardingUrl", "https://sauti.uk/onboarding");
        context.setVariable("helpUrl", "https://sauti.uk/help");

        var html = templateEngine().process("email/welcome", context);
        save("welcome", html);

        assertThat(html)
                .contains("Your Sauti workspace is ready")
                .contains("Welcome, Hairy")
                .contains("Create your agent")
                .contains("Connect a channel")
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
        save("demo-request-received", receivedHtml);
        assertThat(receivedHtml).contains("We received your request").contains("Amina")
                .contains("Nothing is charged and no phone number is bought");

        request.reject("Current pilot capacity is full", java.time.OffsetDateTime.now());
        var rejected = new Context(); rejected.setVariable("request", request);
        var rejectedHtml = templateEngine().process("email/demo-request-rejected", rejected);
        save("demo-request-rejected", rejectedHtml);
        assertThat(rejectedHtml).contains("open this pilot right now")
                .contains("Current pilot capacity is full").contains("reply to this email");
    }

    @Test
    void rendersBookingStatusesWithCalendarWarning() {
        var booking = java.util.Map.of(
                "bookingNumber", "SAT-7F3K2", "service", "Cleaning", "appointmentDate", "Monday, 3 August 2026",
                "appointmentTime", "9:00 AM", "timezone", "Africa/Nairobi (UTC+03:00)", "duration", "1 hour",
                "customerName", "Wanjiru", "customerPhone", "+254700000000", "agentName", "Amina",
                "calendarError", "Google Calendar rejected the event.");
        var confirmed = new Context();
        confirmed.setVariable("booking", booking);
        confirmed.setVariable("notification", java.util.Map.of("heading", "New booking confirmed", "statusLabel", "CONFIRMED",
                "actionTimeLabel", "Confirmation captured at", "actionTime", "3 Aug 2026 at 8:42 AM", "previousAppointment", "", "cancelled", false));
        confirmed.setVariable("calendarSyncFailed", true);
        confirmed.setVariable("bookingsUrl", "https://sauti.uk/bookings");
        var html = templateEngine().process("email/booking-confirmation", confirmed);
        assertThat(html).contains("New booking confirmed").contains("Confirmed").doesNotContain("CONFIRMED")
                .contains("Wanjiru").contains("Your calendar was not updated").contains("Google Calendar rejected the event.")
                .doesNotContain("Previously");
        save("booking-confirmed", html);

        var rescheduled = new Context();
        rescheduled.setVariable("booking", booking);
        rescheduled.setVariable("notification", java.util.Map.of("heading", "Booking rescheduled", "statusLabel", "RESCHEDULED",
                "actionTimeLabel", "Reschedule confirmed at", "actionTime", "3 Aug 2026 at 8:42 AM",
                "previousAppointment", "Friday, 31 July 2026 at 1:00 PM", "cancelled", false));
        rescheduled.setVariable("calendarSyncFailed", false);
        rescheduled.setVariable("bookingsUrl", "https://sauti.uk/bookings");
        var rescheduledHtml = templateEngine().process("email/booking-confirmation", rescheduled);
        assertThat(rescheduledHtml).contains("Previously").contains("Friday, 31 July 2026 at 1:00 PM")
                .doesNotContain("Your calendar was not updated");
        save("booking-rescheduled", rescheduledHtml);
    }

    @Test
    void rendersPaymentPilotAndAdminEmails() {
        var payment = new Context();
        payment.setVariable("businessName", "Hairy");
        payment.setVariable("testMode", true);
        payment.setVariable("purchaseDescription", "Growth plan");
        payment.setVariable("currency", "USD");
        payment.setVariable("amount", "149.00");
        payment.setVariable("paidAt", "10 Aug 2026 at 9:15 AM");
        payment.setVariable("cardLast4", null);
        payment.setVariable("paymentReference", "pay_123");
        payment.setVariable("billingUrl", "https://sauti.uk/billing");
        var paymentHtml = templateEngine().process("email/payment-confirmation", payment);
        assertThat(paymentHtml).contains("Your payment is confirmed").contains("149.00").contains("Test payment")
                .doesNotContain("Card ending");
        save("payment-confirmation", paymentHtml);

        var pilot = new Context();
        pilot.setVariable("invitation", java.util.Map.of("businessName", "Acme Clinic", "contactName", "Amina"));
        pilot.setVariable("acceptUrl", "https://sauti.uk/accept-invite#token=abc");
        var pilotHtml = templateEngine().process("email/pilot-invitation", pilot);
        assertThat(pilotHtml).contains("Activate your workspace").contains("Acme Clinic").contains("72 hours");
        save("pilot-invitation", pilotHtml);

        var request = new com.sauti.demo.DemoRequest("Acme Clinic", "Amina", "owner@example.com", "KE",
                null, "Healthcare", "under-100", "voice", "Answer calls", null);
        var admin = new Context();
        admin.setVariable("request", request);
        admin.setVariable("adminReviewUrl", "https://admin.sauti.uk/admin/demo-requests");
        var adminHtml = templateEngine().process("email/demo-request", admin);
        assertThat(adminHtml).contains("Acme Clinic").contains("Not provided").contains("Review in Sauti Admin");
        save("demo-request", adminHtml);
    }

    @Test
    void noEmailUsesTheOldDarkPalette() throws Exception {
        var dir = java.nio.file.Path.of("src", "main", "resources", "templates", "email");
        try (var files = java.nio.file.Files.list(dir)) {
            for (var file : files.toList()) {
                var source = java.nio.file.Files.readString(file);
                assertThat(source).as(file.getFileName().toString())
                        .doesNotContain("#020a14").doesNotContain("#071827").doesNotContain("#45d8d0")
                        .doesNotContain("#5ee6dc").doesNotContain("#45dbd0").doesNotContain("#45dfd4")
                        .doesNotContain("text-transform:uppercase");
            }
        }
    }

    private void save(String name, String html) {
        try {
            var dir = java.nio.file.Path.of("build", "email-previews");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(name + ".html"), html);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
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
