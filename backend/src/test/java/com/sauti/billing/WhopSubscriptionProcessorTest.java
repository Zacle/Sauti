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
        var processor = new WhopSubscriptionProcessor(events, subscriptions,
                mock(BillingAddOnSubscriptionRepository.class),
                mock(BillingPaymentNotificationRepository.class), tenants, ledger,
                new WhopPlanCatalog("plan_launch_monthly", "", "", "", "", ""), addOns(),
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
                mock(BillingAddOnSubscriptionRepository.class),
                mock(BillingPaymentNotificationRepository.class), mock(TenantRepository.class),
                mock(BillingLedgerService.class), new WhopPlanCatalog("", "", "", "", "", ""),
                addOns(), new ObjectMapper(), "reference-secret", true);

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("deferred");
    }

    @Test
    void synchronizesAddOnMembershipWithoutChangingBasePlan() throws Exception {
        var events = mock(BillingProviderEventRepository.class);
        var subscriptions = mock(BillingSubscriptionRepository.class);
        var addOnSubscriptions = mock(BillingAddOnSubscriptionRepository.class);
        var tenants = mock(TenantRepository.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        tenant.applyBillingSubscription("growth", 750, null, "user_1");
        var reference = new WhopTenantReference("reference-secret").create(tenant.getId());
        var payload = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "id", "msg_addon", "type", "membership.activated", "company_id", "biz_sauti",
                "data", java.util.Map.ofEntries(
                        java.util.Map.entry("id", "mem_addon_1"),
                        java.util.Map.entry("status", "active"),
                        java.util.Map.entry("updated_at", "2026-08-10T12:00:00Z"),
                        java.util.Map.entry("renewal_period_end", "2026-09-10T12:00:00Z"),
                        java.util.Map.entry("metadata", java.util.Map.of(
                                "sauti_tenant_reference", reference,
                                "sauti_purchase_type", "add_on",
                                "sauti_add_on", "agent")),
                        java.util.Map.entry("plan", java.util.Map.of("id", "plan_agent")),
                        java.util.Map.entry("manage_url", "https://whop.com/billing/manage/mem_addon_1")
                )));
        var event = new BillingProviderEvent("whop", "e".repeat(64), "membership.activated", payload);
        when(events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                any(), anyList(), any(OffsetDateTime.class))).thenReturn(List.of(event));
        when(addOnSubscriptions.findByProviderAndProviderSubscriptionId("whop", "mem_addon_1"))
                .thenReturn(Optional.empty());
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        var processor = new WhopSubscriptionProcessor(events, subscriptions, addOnSubscriptions,
                mock(BillingPaymentNotificationRepository.class), tenants, mock(BillingLedgerService.class),
                new WhopPlanCatalog("", "", "", "", "", ""), addOns(),
                new ObjectMapper(), "reference-secret", true);

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("processed");
        assertThat(tenant.getPlan()).isEqualTo("growth");
        var captured = ArgumentCaptor.forClass(BillingAddOnSubscription.class);
        verify(addOnSubscriptions).save(captured.capture());
        assertThat(captured.getValue().getAddOn()).isEqualTo("agent");
        assertThat(captured.getValue().getProviderStatus()).isEqualTo("active");
    }

    @Test
    void appliesPlanChangeToTheExistingWhopMembership() throws Exception {
        var events = mock(BillingProviderEventRepository.class);
        var subscriptions = mock(BillingSubscriptionRepository.class);
        var tenants = mock(TenantRepository.class);
        var ledger = mock(BillingLedgerService.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        tenant.applyBillingSubscription("growth", 750, null, "user_1");
        var existing = new BillingSubscription(tenant.getId(), "whop", "mem_1");
        existing.synchronize("user_1", "mem_1", "prod_sauti", "plan_growth_monthly",
                "growth", "monthly", "active", true, null, null, null,
                OffsetDateTime.parse("2026-08-09T12:00:00Z"), "", "",
                "https://whop.com/billing/manage/mem_1");
        var payload = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "id", "msg_scale", "type", "membership.activated", "company_id", "biz_sauti",
                "data", java.util.Map.ofEntries(
                        java.util.Map.entry("id", "mem_1"),
                        java.util.Map.entry("status", "active"),
                        java.util.Map.entry("updated_at", "2026-08-10T12:00:00Z"),
                        java.util.Map.entry("renewal_period_end", "2026-09-10T12:00:00Z"),
                        java.util.Map.entry("metadata", java.util.Map.of()),
                        java.util.Map.entry("plan", java.util.Map.of("id", "plan_scale_monthly")),
                        java.util.Map.entry("product", java.util.Map.of("id", "prod_sauti")),
                        java.util.Map.entry("user", java.util.Map.of("id", "user_1")),
                        java.util.Map.entry("manage_url", "https://whop.com/billing/manage/mem_1")
                )));
        var event = new BillingProviderEvent("whop", "f".repeat(64), "membership.activated", payload);
        when(events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                any(), anyList(), any(OffsetDateTime.class))).thenReturn(List.of(event));
        when(subscriptions.findByProviderAndProviderSubscriptionId("whop", "mem_1"))
                .thenReturn(Optional.of(existing));
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(ledger.account(tenant.getId())).thenReturn(new BillingAccount(tenant.getId()));
        var processor = new WhopSubscriptionProcessor(events, subscriptions,
                mock(BillingAddOnSubscriptionRepository.class),
                mock(BillingPaymentNotificationRepository.class), tenants, ledger,
                new WhopPlanCatalog("", "", "", "", "plan_scale_monthly", ""), addOns(),
                new ObjectMapper(), "reference-secret", true);

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("processed");
        assertThat(tenant.getPlan()).isEqualTo("scale");
        assertThat(tenant.getMonthlyMinutesLimit()).isEqualTo(2500);
        assertThat(existing.getPlan()).isEqualTo("scale");
    }

    @Test
    void queuesOneBusinessEmailForVerifiedSuccessfulPayment() throws Exception {
        var events = mock(BillingProviderEventRepository.class);
        var subscriptions = mock(BillingSubscriptionRepository.class);
        var addOnSubscriptions = mock(BillingAddOnSubscriptionRepository.class);
        var notifications = mock(BillingPaymentNotificationRepository.class);
        var tenants = mock(TenantRepository.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var membership = new BillingSubscription(tenant.getId(), "whop", "mem_1");
        var payload = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "id", "msg_payment", "type", "payment.succeeded", "company_id", "biz_sauti",
                "data", java.util.Map.ofEntries(
                        java.util.Map.entry("id", "pay_1"),
                        java.util.Map.entry("membership", java.util.Map.of("id", "mem_1")),
                        java.util.Map.entry("plan", java.util.Map.of("id", "plan_growth_monthly")),
                        java.util.Map.entry("total", 149.00),
                        java.util.Map.entry("currency", "usd"),
                        java.util.Map.entry("paid_at", "2026-08-10T12:00:00Z"),
                        java.util.Map.entry("card_last4", "4242")
                )));
        var event = new BillingProviderEvent("whop", "a".repeat(64), "payment.succeeded", payload);
        when(events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                any(), anyList(), any(OffsetDateTime.class))).thenReturn(List.of(event));
        when(notifications.findByProviderAndProviderPaymentId("whop", "pay_1")).thenReturn(Optional.empty());
        when(subscriptions.findByProviderAndProviderSubscriptionId("whop", "mem_1"))
                .thenReturn(Optional.of(membership));
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        var processor = new WhopSubscriptionProcessor(events, subscriptions, addOnSubscriptions,
                notifications, tenants, mock(BillingLedgerService.class),
                new WhopPlanCatalog("", "", "plan_growth_monthly", "", "", ""), addOns(),
                new ObjectMapper(), "reference-secret", true);

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("deferred");
        var captured = ArgumentCaptor.forClass(BillingPaymentNotification.class);
        verify(notifications).save(captured.capture());
        assertThat(captured.getValue().getRecipientEmail()).isEqualTo("owner@example.com");
        assertThat(captured.getValue().getPurchaseDescription()).isEqualTo("Growth plan (monthly)");
        assertThat(captured.getValue().getCurrency()).isEqualTo("USD");
        assertThat(captured.getValue().isTestMode()).isTrue();
    }

    private WhopAddOnCatalog addOns() {
        return new WhopAddOnCatalog("plan_agent", "plan_line", "plan_number", "plan_voice", "plan_messaging");
    }
}
