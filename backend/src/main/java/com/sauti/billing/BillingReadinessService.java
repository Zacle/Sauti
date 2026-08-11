package com.sauti.billing;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final BillingFinancialReconciliationService financialReconciliation;
    private final String activeProvider;
    private final boolean sandbox;
    private final boolean apiConfigured;
    private final boolean webhookConfigured;
    private final boolean tenantSigningConfigured;

    public BillingReadinessService(
            WhopPlanCatalog plans, WhopAddOnCatalog addOns,
            BillingProviderEvidenceRepository evidence, BillingProviderEventRepository events,
            BillingFinancialReconciliationService financialReconciliation,
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
        this.financialReconciliation = financialReconciliation;
        this.activeProvider = clean(activeProvider).toLowerCase();
        this.sandbox = sandbox;
        this.apiConfigured = configured(apiKey) && configured(companyId);
        this.webhookConfigured = configured(webhookSecret) && configured(companyId);
        this.tenantSigningConfigured = configured(tenantReferenceSecret);
    }

    @Transactional(readOnly = true)
    public Readiness readiness() {
        var stored = evidence.findAllByProviderAndTestModeOrderByOccurredAtAsc(PROVIDER, true);
        var variants = plans.all().stream().map(plan -> variant(plan, stored)).toList();
        var configuredPlans = variants.stream().filter(PlanVariant::configured).count();
        var retrying = events.countByProviderAndStatus(PROVIDER, "retrying");
        var failed = events.countByProviderAndStatus(PROVIDER, "failed");
        var lifecycle = representativeLifecycle(stored);
        var setupReady = PROVIDER.equals(activeProvider) && apiConfigured && webhookConfigured
                && tenantSigningConfigured && configuredPlans == variants.size();
        var status = !setupReady ? "configuration_missing"
                : failed > 0 ? "attention"
                : "accepted".equals(lifecycle.status()) ? "ready" : "in_progress";
        var lastEvidenceAt = stored.isEmpty() ? null : stored.get(stored.size() - 1).getOccurredAt();
        var sandboxFinancial = financialReconciliation.summarize(true);
        var liveFinancial = financialReconciliation.summarize(false);
        return new Readiness(PROVIDER, sandbox ? "sandbox" : "live", status,
                apiConfigured, webhookConfigured, tenantSigningConfigured,
                plans.fullyConfigured(), addOns.fullyConfigured(), configuredPlans,
                stored.size(), retrying, failed, lastEvidenceAt, lifecycle,
                variants, sandboxFinancial, liveFinancial, OffsetDateTime.now());
    }

    private PlanVariant variant(WhopPlanCatalog.Plan plan, List<BillingProviderEvidence> stored) {
        var configured = !plan.planId().isBlank();
        var latest = stored.stream()
                .filter(item -> plan.planId().equals(item.getProviderPlanId()))
                .map(BillingProviderEvidence::getOccurredAt)
                .max(Comparator.naturalOrder()).orElse(null);
        return new PlanVariant(plan.plan(), plan.interval(), configured,
                configured ? mask(plan.planId()) : null,
                configured ? "configured" : "configuration_missing", latest);
    }

    private RepresentativeLifecycle representativeLifecycle(List<BillingProviderEvidence> stored) {
        var grouped = new LinkedHashMap<String, LifecycleAccumulator>();
        for (var item : stored) {
            var membershipId = clean(item.getProviderMembershipId());
            if (membershipId.isBlank()) continue;
            grouped.computeIfAbsent(membershipId, ignored -> new LifecycleAccumulator())
                    .accept(item);
        }
        return grouped.entrySet().stream()
                .map(entry -> entry.getValue().view(entry.getKey()))
                .filter(item -> item.membershipActivatedAt() != null)
                .max(Comparator.comparingInt(BillingReadinessService::completionScore)
                        .thenComparing(item -> item.membershipActivatedAt(), Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseGet(() -> new RepresentativeLifecycle("not_started", null, null, null,
                        null, null, null));
    }

    private static int completionScore(RepresentativeLifecycle lifecycle) {
        if (lifecycle.cancellationObservedAt() != null && lifecycle.paymentSucceededAt() != null) return 3;
        if (lifecycle.paymentSucceededAt() != null) return 2;
        if (lifecycle.membershipActivatedAt() != null) return 1;
        return 0;
    }

    private final class LifecycleAccumulator {
        private String planId;
        private OffsetDateTime activatedAt;
        private OffsetDateTime paidAt;
        private OffsetDateTime canceledAt;

        void accept(BillingProviderEvidence item) {
            if (planId == null && item.getProviderPlanId() != null) planId = item.getProviderPlanId();
            if ("membership.activated".equals(item.getEventName())) activatedAt = latest(activatedAt, item.getOccurredAt());
            if ("payment.succeeded".equals(item.getEventName())) paidAt = latest(paidAt, item.getOccurredAt());
            if (CANCELLATION_EVENTS.contains(item.getEventName())) canceledAt = latest(canceledAt, item.getOccurredAt());
        }

        RepresentativeLifecycle view(String membershipId) {
            var selection = plans.byPlanId(planId).orElse(null);
            var status = activatedAt == null ? "not_started"
                    : paidAt == null ? "awaiting_payment"
                    : canceledAt == null ? "awaiting_cancellation" : "accepted";
            return new RepresentativeLifecycle(status,
                    selection == null ? null : selection.plan(),
                    selection == null ? null : selection.interval(),
                    mask(membershipId), activatedAt, paidAt, canceledAt);
        }
    }

    private static OffsetDateTime latest(OffsetDateTime current, OffsetDateTime candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static boolean configured(String value) { return !clean(value).isBlank(); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String mask(String value) {
        var clean = clean(value);
        return clean.isBlank() ? null : clean.length() <= 8 ? clean : "…" + clean.substring(clean.length() - 8);
    }

    public record Readiness(String provider, String environment, String status,
                            boolean apiConfigured, boolean webhookConfigured,
                            boolean tenantSigningConfigured, boolean plansConfigured,
                            boolean addOnsConfigured, long configuredPlans,
                            long normalizedSandboxEvents, long retryingProviderEvents,
                            long failedProviderEvents, OffsetDateTime lastSandboxEvidenceAt,
                            RepresentativeLifecycle representativeLifecycle,
                            List<PlanVariant> variants,
                            BillingFinancialReconciliationService.FinancialSummary sandboxFinancial,
                            BillingFinancialReconciliationService.FinancialSummary liveFinancial,
                            OffsetDateTime generatedAt) { }

    public record RepresentativeLifecycle(String status, String plan, String interval,
                                          String membershipReference,
                                          OffsetDateTime membershipActivatedAt,
                                          OffsetDateTime paymentSucceededAt,
                                          OffsetDateTime cancellationObservedAt) { }

    public record PlanVariant(String plan, String interval, boolean configured,
                              String planReference, String status,
                              OffsetDateTime sandboxEvidenceAt) { }
}
