package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingReadinessServiceTest {
    @Test
    void requiresOneRepresentativeLifecycleWithoutRequiringSixPaidMemberships() {
        var evidence = mock(BillingProviderEvidenceRepository.class);
        var events = mock(BillingProviderEventRepository.class);
        var financial = mock(BillingFinancialReconciliationService.class);
        var plans = plans();
        var activated = evidence("membership.activated", "membership", "mem_1", "launch_m", "active",
                "2026-08-11T08:00:00Z");
        var paid = evidence("payment.succeeded", "payment", "mem_1", "launch_m", "succeeded",
                "2026-08-11T08:01:00Z");
        var canceled = evidence("membership.cancel_at_period_end_changed", "membership", "mem_1",
                "launch_m", "canceling", "2026-08-11T08:02:00Z");
        when(evidence.findAllByProviderAndTestModeOrderByOccurredAtAsc("whop", true))
                .thenReturn(List.of(activated, paid, canceled));
        when(financial.summarize(true)).thenReturn(summary("sandbox"));
        when(financial.summarize(false)).thenReturn(summary("live"));
        var service = new BillingReadinessService(plans, addOns(), evidence, events, financial,
                "whop", true, "key", "company", "webhook", "reference");

        var result = service.readiness();

        assertThat(result.status()).isEqualTo("ready");
        assertThat(result.configuredPlans()).isEqualTo(6);
        assertThat(result.representativeLifecycle().status()).isEqualTo("accepted");
        assertThat(result.representativeLifecycle().plan()).isEqualTo("launch");
        assertThat(result.variants()).allMatch(item -> "configured".equals(item.status()));
        assertThat(result.variants().get(0).sandboxEvidenceAt()).isNotNull();
        assertThat(result.variants().get(1).sandboxEvidenceAt()).isNull();
    }

    @Test
    void keepsCancellationAsTheOnlyOutstandingRepresentativeStep() {
        var evidence = mock(BillingProviderEvidenceRepository.class);
        when(evidence.findAllByProviderAndTestModeOrderByOccurredAtAsc("whop", true))
                .thenReturn(List.of(
                        evidence("membership.activated", "membership", "mem_1", "growth_m", "active",
                                "2026-08-11T08:00:00Z"),
                        evidence("payment.succeeded", "payment", "mem_1", "growth_m", "succeeded",
                                "2026-08-11T08:01:00Z")));
        var financial = mock(BillingFinancialReconciliationService.class);
        when(financial.summarize(true)).thenReturn(summary("sandbox"));
        when(financial.summarize(false)).thenReturn(summary("live"));
        var service = new BillingReadinessService(plans(), addOns(), evidence,
                mock(BillingProviderEventRepository.class), financial,
                "whop", true, "key", "company", "webhook", "reference");

        var result = service.readiness();

        assertThat(result.status()).isEqualTo("in_progress");
        assertThat(result.representativeLifecycle().status()).isEqualTo("awaiting_cancellation");
    }

    @Test
    void reportsMissingConfigurationWithoutExposingCredentialValues() {
        var evidence = mock(BillingProviderEvidenceRepository.class);
        when(evidence.findAllByProviderAndTestModeOrderByOccurredAtAsc("whop", true)).thenReturn(List.of());
        var financial = mock(BillingFinancialReconciliationService.class);
        when(financial.summarize(true)).thenReturn(summary("sandbox"));
        when(financial.summarize(false)).thenReturn(summary("live"));
        var service = new BillingReadinessService(
                new WhopPlanCatalog("", "", "", "", "", ""), addOns(), evidence,
                mock(BillingProviderEventRepository.class), financial,
                "whop", true, "", "", "", "");

        var result = service.readiness();

        assertThat(result.status()).isEqualTo("configuration_missing");
        assertThat(result.apiConfigured()).isFalse();
        assertThat(result.webhookConfigured()).isFalse();
        assertThat(result.variants()).allMatch(item -> item.planReference() == null);
    }

    private static BillingProviderEvidence evidence(String eventName, String type, String membershipId,
                                                     String planId, String status, String occurredAt) {
        return new BillingProviderEvidence(UUID.randomUUID(), UUID.randomUUID(), "whop", type,
                eventName, type + "_1", "payment".equals(type) ? "pay_1" : null,
                membershipId, planId, status,
                "payment".equals(type) ? new BigDecimal("49.00") : null,
                "payment".equals(type) ? "USD" : null, true, OffsetDateTime.parse(occurredAt));
    }

    private static WhopPlanCatalog plans() {
        return new WhopPlanCatalog("launch_m", "launch_a", "growth_m", "growth_a", "scale_m", "scale_a");
    }

    private static WhopAddOnCatalog addOns() {
        return new WhopAddOnCatalog("agent", "line", "number", "voice", "messaging");
    }

    private static BillingFinancialReconciliationService.FinancialSummary summary(String environment) {
        return new BillingFinancialReconciliationService.FinancialSummary(environment, 0, 0, 0, 0,
                0, 0, 0, List.of(), List.of(), null);
    }
}
