package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingReadinessServiceTest {
    @Test
    void requiresStoredSandboxLifecycleEvidenceForEveryVariant() {
        var evidence = mock(BillingProviderEvidenceRepository.class);
        var events = mock(BillingProviderEventRepository.class);
        var plans = new WhopPlanCatalog("launch_m", "launch_a", "growth_m", "growth_a", "scale_m", "scale_a");
        var observedAt = OffsetDateTime.parse("2026-08-11T08:00:00Z");
        var record = evidence(observedAt);
        when(evidence.findFirstByProviderAndTestModeAndProviderPlanIdAndEventNameOrderByOccurredAtDesc(
                "whop", true, "launch_m", "membership.activated")).thenReturn(Optional.of(record));
        when(evidence.findFirstByProviderAndTestModeAndProviderPlanIdAndEventNameOrderByOccurredAtDesc(
                "whop", true, "launch_m", "payment.succeeded")).thenReturn(Optional.of(record));
        when(evidence.findFirstByProviderAndTestModeAndProviderPlanIdAndEventNameInOrderByOccurredAtDesc(
                org.mockito.ArgumentMatchers.eq("whop"), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("launch_m"), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Optional.of(record));
        when(evidence.countByProviderAndTestMode("whop", true)).thenReturn(3L);
        var service = new BillingReadinessService(plans, addOns(), evidence, events,
                "whop", true, "key", "company", "webhook", "reference");

        var result = service.readiness();

        assertThat(result.status()).isEqualTo("in_progress");
        assertThat(result.configuredPlans()).isEqualTo(6);
        assertThat(result.acceptedPlans()).isEqualTo(1);
        assertThat(result.variants().get(0).status()).isEqualTo("accepted");
        assertThat(result.variants().get(1).status()).isEqualTo("awaiting_activation");
        assertThat(result.variants().get(0).planReference()).endsWith("launch_m");
    }

    @Test
    void reportsMissingConfigurationWithoutExposingCredentialValues() {
        var service = new BillingReadinessService(
                new WhopPlanCatalog("", "", "", "", "", ""), addOns(),
                mock(BillingProviderEvidenceRepository.class), mock(BillingProviderEventRepository.class),
                "whop", true, "", "", "", "");

        var result = service.readiness();

        assertThat(result.status()).isEqualTo("configuration_missing");
        assertThat(result.apiConfigured()).isFalse();
        assertThat(result.webhookConfigured()).isFalse();
        assertThat(result.variants()).allMatch(item -> item.planReference() == null);
    }

    private static BillingProviderEvidence evidence(OffsetDateTime occurredAt) {
        return new BillingProviderEvidence(UUID.randomUUID(), UUID.randomUUID(), "whop", "membership",
                "membership.activated", "mem_1", null, "mem_1", "launch_m",
                "active", null, null, true, occurredAt);
    }

    private static WhopAddOnCatalog addOns() {
        return new WhopAddOnCatalog("agent", "line", "number", "voice", "messaging");
    }
}
