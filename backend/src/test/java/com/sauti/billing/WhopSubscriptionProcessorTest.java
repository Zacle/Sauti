package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WhopSubscriptionProcessorTest {
    @Test
    void appliesVerifiedMembershipWhileKeepingObserveMode() throws Exception {
        var events = mock(BillingProviderEventRepository.class);
        var subscriptions = mock(BillingSubscriptionRepository.class);
        var tenants = mock(TenantRepository.class);
        var ledger = mock(BillingLedgerService.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var account = new BillingAccount(tenant.getId());
        var reference = new WhopTenantReference("reference-secret").create(tenant.getId());
        var payload = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "id", "msg_1", "type", "membership.activated", "company_id", "biz_sauti",
                "data", java.util.Map.ofEntries(
                        java.util.Map.entry("id", "mem_1"),
                        java.util.Map.entry("status", "active"),
                        java.util.Map.entry("updated_at", "2026-08-09T12:00:00Z"),
                        java.util.Map.entry("renewal_period_end", "2026-09-09T12:00:00Z"),
                        java.util.Map.entry("metadata", java.util.Map.of("sauti_tenant_reference", reference)),
                        java.util.Map.entry("plan", java.util.Map.of("id", "plan_launch_monthly")),
                        java.util.Map.entry("product", java.util.Map.of("id", "prod_sauti")),
                        java.util.Map.entry("user", java.util.Map.of("id", "user_1")),
                        java.util.Map.entry("manage_url", "https://whop.com/billing/manage/mem_1")
                )));
        var event = new BillingProviderEvent("whop", "c".repeat(64), "membership.activated", payload);
        when(events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                any(), anyList(), any(OffsetDateTime.class))).thenReturn(List.of(event));
        when(subscriptions.findByProviderAndProviderSubscriptionId("whop", "mem_1")).thenReturn(Optional.empty());
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.empty());
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(ledger.account(tenant.getId())).thenReturn(account);
        var processor = new WhopSubscriptionProcessor(events, subscriptions, tenants, ledger,
                new WhopPlanCatalog("plan_launch_monthly", "", "", "", "", ""),
                new ObjectMapper(), "reference-secret", true);

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("processed");
        assertThat(tenant.getPlan()).isEqualTo("launch");
        assertThat(account.getEnforcementMode()).isEqualTo("observe");
        var subscription = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(subscriptions).save(subscription.capture());
        assertThat(subscription.getValue().getProvider()).isEqualTo("whop");
        assertThat(subscription.getValue().getProviderStatus()).isEqualTo("active");
    }

    @Test
    void retainsFinancialEventsAsDeferredInsteadOfClaimingReconciliation() {
        var events = mock(BillingProviderEventRepository.class);
        var event = new BillingProviderEvent("whop", "d".repeat(64), "refund.created", "{}");
        when(events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                any(), anyList(), any(OffsetDateTime.class))).thenReturn(List.of(event));
        var processor = new WhopSubscriptionProcessor(events, mock(BillingSubscriptionRepository.class),
                mock(TenantRepository.class), mock(BillingLedgerService.class),
                new WhopPlanCatalog("", "", "", "", "", ""), new ObjectMapper(), "reference-secret", true);

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("deferred");
    }
}
