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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TwoCheckoutSubscriptionProcessorTest {
    @Test
    void appliesSignedWorkspaceSubscriptionWhileKeepingObserveMode() throws Exception {
        var secret = "test-secret";
        var events = mock(BillingProviderEventRepository.class);
        var subscriptions = mock(BillingSubscriptionRepository.class);
        var tenants = mock(TenantRepository.class);
        var ledger = mock(BillingLedgerService.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var account = new BillingAccount(tenant.getId());
        var reference = new TwoCheckoutTenantReference(secret).create(tenant.getId());
        var payload = new ObjectMapper().writeValueAsString(Map.ofEntries(
                Map.entry("LICENSE_CODE", "subscription-1"),
                Map.entry("EXTERNAL_CUSTOMER_REFERENCE", reference),
                Map.entry("AVANGATE_CUSTOMER_REFERENCE", "customer-1"),
                Map.entry("LICENSE_PRODUCT_CODE", "launch-monthly"),
                Map.entry("STATUS", "ACTIVE"),
                Map.entry("LAST_ORDER_REFERENCE", "order-1"),
                Map.entry("NEXT_RENEWAL_DATE", "2026-09-03 00:00:00"),
                Map.entry("EXPIRATION_DATE", "2026-09-03"),
                Map.entry("DATE_UPDATED", "2026-08-03 00:00:00"),
                Map.entry("TEST", "1")
        ));
        var event = new BillingProviderEvent("2checkout", "b".repeat(64), "LICENCE_CHANGE", payload);
        when(events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                any(), anyList(), any(OffsetDateTime.class))).thenReturn(List.of(event));
        when(subscriptions.findByProviderAndProviderSubscriptionId("2checkout", "subscription-1"))
                .thenReturn(Optional.empty());
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.empty());
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(ledger.account(tenant.getId())).thenReturn(account);
        var processor = new TwoCheckoutSubscriptionProcessor(events, subscriptions, tenants, ledger,
                plans(), new ObjectMapper(), secret);

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("processed");
        assertThat(tenant.getPlan()).isEqualTo("launch");
        assertThat(tenant.getMonthlyMinutesLimit()).isEqualTo(100);
        assertThat(account.getStatus()).isEqualTo("active");
        assertThat(account.getEnforcementMode()).isEqualTo("observe");
        var subscription = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(subscriptions).save(subscription.capture());
        assertThat(subscription.getValue().getProvider()).isEqualTo("2checkout");
        assertThat(subscription.getValue().getProviderStatus()).isEqualTo("active");
    }

    private TwoCheckoutPlanCatalog plans() {
        return new TwoCheckoutPlanCatalog(
                "launch-monthly", "https://secure.2checkout.com/order/checkout.php?PRODS=101",
                "", "", "", "", "", "", "", "", "", "");
    }
}
