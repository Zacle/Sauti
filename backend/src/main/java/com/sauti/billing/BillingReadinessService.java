package com.sauti.billing;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingReadinessService {
    private static final String PROVIDER = "whop";
    private static final List<String> CANCELLATION_EVENTS = List.of(
            "membership.deactivated", "membership.cancel_at_period_end_changed");
    private final WhopPlanCatalog plans;
    private final WhopAddOnCatalog addOns;
    private final BillingProviderEvidenceRepository evidence;
    private final BillingProviderEventRepository events;
    private final String activeProvider;
    private final boolean sandbox;
    private final boolean apiConfigured;
    private final boolean webhookConfigured;
    private final boolean tenantSigningConfigured;

    public BillingReadinessService(
            WhopPlanCatalog plans, WhopAddOnCatalog addOns,
            BillingProviderEvidenceRepository evidence, BillingProviderEventRepository events,
            @Value("${sauti.billing.provider:whop}") String activeProvider,
            @Value("${sauti.billing.whop.sandbox:false}") boolean sandbox,
            @Value("${sauti.billing.whop.api-key:}") String apiKey,
            @Value("${sauti.billing.whop.company-id:}") String companyId,
            @Value("${sauti.billing.whop.webhook-secret:}") String webhookSecret,
            @Value("${sauti.billing.whop.tenant-reference-secret:}") String tenantReferenceSecret) {
        this.plans = plans;
        this.addOns = addOns;
        this.evidence = evidence;
        this.events = events;
        this.activeProvider = clean(activeProvider).toLowerCase();
        this.sandbox = sandbox;
        this.apiConfigured = configured(apiKey) && configured(companyId);
        this.webhookConfigured = configured(webhookSecret) && configured(companyId);
        this.tenantSigningConfigured = configured(tenantReferenceSecret);
    }

    @Transactional(readOnly = true)
    public Readiness readiness() {
        var variants = plans.all().stream().map(this::variant).toList();
        var accepted = variants.stream().filter(item -> "accepted".equals(item.status())).count();
        var configuredPlans = variants.stream().filter(PlanVariant::configured).count();
        var retrying = events.countByProviderAndStatus(PROVIDER, "retrying");
        var failed = events.countByProviderAndStatus(PROVIDER, "failed");
        var setupReady = PROVIDER.equals(activeProvider) && apiConfigured && webhookConfigured
                && tenantSigningConfigured && configuredPlans == variants.size();
        var status = !setupReady ? "configuration_missing"
                : failed > 0 ? "attention"
                : accepted == variants.size() ? "ready" : "in_progress";
        var lastEvidenceAt = evidence.findFirstByProviderAndTestModeOrderByOccurredAtDesc(PROVIDER, true)
                .map(BillingProviderEvidence::getOccurredAt).orElse(null);
        return new Readiness(PROVIDER, sandbox ? "sandbox" : "live", status,
                apiConfigured, webhookConfigured, tenantSigningConfigured,
                plans.fullyConfigured(), addOns.fullyConfigured(), configuredPlans, accepted,
                evidence.countByProviderAndTestMode(PROVIDER, true), retrying, failed,
                lastEvidenceAt, variants, OffsetDateTime.now());
    }

    private PlanVariant variant(WhopPlanCatalog.Plan plan) {
        var configured = !plan.planId().isBlank();
        var activated = observed(plan.planId(), "membership.activated");
        var paid = observed(plan.planId(), "payment.succeeded");
        var canceled = configured ? evidence
                .findFirstByProviderAndTestModeAndProviderPlanIdAndEventNameInOrderByOccurredAtDesc(
                        PROVIDER, true, plan.planId(), CANCELLATION_EVENTS)
                .map(BillingProviderEvidence::getOccurredAt).orElse(null) : null;
        var status = !configured ? "configuration_missing"
                : activated == null ? "awaiting_activation"
                : paid == null ? "awaiting_payment"
                : canceled == null ? "awaiting_cancellation" : "accepted";
        return new PlanVariant(plan.plan(), plan.interval(), configured,
                configured ? mask(plan.planId()) : null, status, activated, paid, canceled);
    }

    private OffsetDateTime observed(String planId, String eventName) {
        if (planId == null || planId.isBlank()) return null;
        return evidence.findFirstByProviderAndTestModeAndProviderPlanIdAndEventNameOrderByOccurredAtDesc(
                        PROVIDER, true, planId, eventName)
                .map(BillingProviderEvidence::getOccurredAt).orElse(null);
    }

    private static boolean configured(String value) { return !clean(value).isBlank(); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String mask(String value) {
        var clean = clean(value);
        return clean.length() <= 8 ? clean : "…" + clean.substring(clean.length() - 8);
    }

    public record Readiness(String provider, String environment, String status,
                            boolean apiConfigured, boolean webhookConfigured,
                            boolean tenantSigningConfigured, boolean plansConfigured,
                            boolean addOnsConfigured, long configuredPlans, long acceptedPlans,
                            long normalizedSandboxEvents, long retryingProviderEvents,
                            long failedProviderEvents, OffsetDateTime lastSandboxEvidenceAt,
                            List<PlanVariant> variants, OffsetDateTime generatedAt) { }

    public record PlanVariant(String plan, String interval, boolean configured,
                              String planReference, String status,
                              OffsetDateTime membershipActivatedAt,
                              OffsetDateTime paymentSucceededAt,
                              OffsetDateTime cancellationObservedAt) { }
}
