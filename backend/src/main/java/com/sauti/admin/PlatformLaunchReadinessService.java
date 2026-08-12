package com.sauti.admin;

import com.sauti.billing.BillingReadinessService;
import com.sauti.reliability.ReliabilityDrillService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformLaunchReadinessService {
    public static final String APPROVAL_CONFIRMATION = "APPROVE GENERAL AVAILABILITY";
    private final PlatformLaunchReadinessRepository reviews;
    private final PlatformAdminAuditService audit;
    private final BillingReadinessService billing;
    private final ReliabilityDrillService drills;
    private final Environment environment;

    public PlatformLaunchReadinessService(PlatformLaunchReadinessRepository reviews,
                                          PlatformAdminAuditService audit,
                                          BillingReadinessService billing,
                                          ReliabilityDrillService drills,
                                          Environment environment) {
        this.reviews = reviews;
        this.audit = audit;
        this.billing = billing;
        this.drills = drills;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public Readiness get() {
        return view(reviews.findById(PlatformLaunchReadiness.ID).orElse(null));
    }

    @Transactional
    public Readiness update(UpdateReview command, String actor) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var review = reviews.findByIdForUpdate(PlatformLaunchReadiness.ID)
                .orElseGet(() -> new PlatformLaunchReadiness(actor, now));
        review.review(command.securityReviewCompleted(), command.privacyLegalReviewCompleted(),
                command.googleVerificationCompleted(), command.liveAcceptanceCompleted(),
                command.notes(), actor, now);
        var checks = automatedChecks();
        if (command.generalAvailabilityApproved()) {
            if (!APPROVAL_CONFIRMATION.equals(command.confirmation())) {
                throw new IllegalArgumentException("Exact general availability confirmation is required");
            }
            if (checks.stream().anyMatch(check -> !check.passed()) || !manualComplete(review)) {
                throw new IllegalStateException("General availability cannot be approved while launch checks are incomplete");
            }
            review.approve();
        }
        reviews.save(review);
        audit.record(actor, review.isGeneralAvailabilityApproved()
                        ? "platform.launch.approved" : "platform.launch.reviewed",
                "platform_launch", PlatformLaunchReadiness.ID,
                review.isGeneralAvailabilityApproved()
                        ? "General availability approved after all launch gates passed"
                        : "Phase 4 launch readiness review updated");
        return view(review, checks);
    }

    private Readiness view(PlatformLaunchReadiness review) {
        return view(review, automatedChecks());
    }

    private Readiness view(PlatformLaunchReadiness review, List<Check> checks) {
        var manual = review == null ? new ManualReview(false, false, false, false,
                false, null, null, null) : new ManualReview(
                review.isSecurityReviewCompleted(), review.isPrivacyLegalReviewCompleted(),
                review.isGoogleVerificationCompleted(), review.isLiveAcceptanceCompleted(),
                review.isGeneralAvailabilityApproved(), review.getNotes(),
                review.getReviewedBy(), review.getReviewedAt());
        var automatedBlocking = checks.stream().filter(check -> !check.passed()).count();
        var manualBlocking = List.of(manual.securityReviewCompleted(), manual.privacyLegalReviewCompleted(),
                manual.googleVerificationCompleted(), manual.liveAcceptanceCompleted())
                .stream().filter(value -> !value).count();
        var status = manual.generalAvailabilityApproved() && automatedBlocking == 0 && manualBlocking == 0
                ? "approved" : automatedBlocking > 0 ? "blocked" : "review_pending";
        return new Readiness(status, automatedBlocking, manualBlocking, checks, manual,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private List<Check> automatedChecks() {
        var production = Arrays.asList(environment.getActiveProfiles()).contains("production");
        var origins = property("sauti.cors.allowed-origins");
        var explicitOrigins = !origins.isBlank() && Arrays.stream(origins.split(","))
                .map(String::trim).allMatch(value -> value.startsWith("https://")
                        && !value.contains("*") && !value.contains("localhost"));
        var completedDrill = drills.recent().stream().anyMatch(drill -> "resolved".equals(drill.status())
                && drill.detectionEmailSentAt() != null && drill.recoveryEmailSentAt() != null);
        var billingReady = "ready".equals(billing.readiness().status());
        return List.of(
                check("production_profile", "Production runtime profile", production,
                        "The production Spring profile must be active."),
                check("registration_closed", "Public registration closed",
                        !bool("sauti.auth.public-registration-enabled", true),
                        "Keep acquisition controlled until general availability is approved."),
                check("dev_tokens_hidden", "Development tokens hidden",
                        !bool("sauti.auth.expose-dev-tokens", true),
                        "Development authentication tokens must never be exposed."),
                check("https_origins", "Explicit HTTPS origins", explicitOrigins,
                        "CORS must contain only explicit HTTPS origins."),
                check("platform_admin", "Platform administrator configured",
                        configured("sauti.admin.emails"),
                        "At least one platform administrator email is required."),
                check("support_email", "Production support email configured",
                        productionEmail("sauti.email.reply-to"),
                        "Configure a non-local support reply-to address."),
                check("reliability_drill", "Production reliability drill completed", completedDrill,
                        "Complete a drill with detection and recovery email evidence."),
                check("billing_acceptance", "Billing lifecycle accepted", billingReady,
                        "Complete the Phase 3 billing readiness lifecycle."));
    }

    private boolean manualComplete(PlatformLaunchReadiness review) {
        return review.isSecurityReviewCompleted() && review.isPrivacyLegalReviewCompleted()
                && review.isGoogleVerificationCompleted() && review.isLiveAcceptanceCompleted();
    }

    private Check check(String key, String label, boolean passed, String action) {
        return new Check(key, label, passed, passed ? "ready" : "blocked", action);
    }

    private boolean productionEmail(String key) {
        var value = property(key);
        return value.contains("@") && !value.endsWith(".local");
    }

    private boolean configured(String key) { return !property(key).isBlank(); }
    private boolean bool(String key, boolean fallback) {
        return environment.getProperty(key, Boolean.class, fallback);
    }
    private String property(String key) { return environment.getProperty(key, "").trim(); }

    public record UpdateReview(boolean securityReviewCompleted,
                               boolean privacyLegalReviewCompleted,
                               boolean googleVerificationCompleted,
                               boolean liveAcceptanceCompleted,
                               boolean generalAvailabilityApproved,
                               String confirmation, String notes) { }
    public record Readiness(String status, long automatedBlockingChecks, long manualBlockingChecks,
                            List<Check> automatedChecks, ManualReview manualReview,
                            OffsetDateTime generatedAt) { }
    public record Check(String key, String label, boolean passed, String status, String action) { }
    public record ManualReview(boolean securityReviewCompleted, boolean privacyLegalReviewCompleted,
                               boolean googleVerificationCompleted, boolean liveAcceptanceCompleted,
                               boolean generalAvailabilityApproved, String notes,
                               String reviewedBy, OffsetDateTime reviewedAt) { }
}
