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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LemonSqueezySubscriptionProcessorTest {
    @Test
    void appliesConfiguredSubscriptionWhileKeepingObserveMode() {
        var events = mock(BillingProviderEventRepository.class);
        var subscriptions = mock(BillingSubscriptionRepository.class);
        var tenants = mock(TenantRepository.class);
        var ledger = mock(BillingLedgerService.class);
        var plans = new LemonSqueezyPlanCatalog("101", "", "", "", "", "");
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var account = new BillingAccount(tenant.getId());
        var event = new BillingProviderEvent("a".repeat(64), "subscription_created", payload(tenant.getId()));
        when(events.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(anyList(), any(OffsetDateTime.class)))
                .thenReturn(List.of(event));
        when(subscriptions.findByProviderSubscriptionId("sub-1")).thenReturn(Optional.empty());
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.empty());
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(ledger.account(tenant.getId())).thenReturn(account);
        var processor = new LemonSqueezySubscriptionProcessor(
                events, subscriptions, tenants, ledger, plans, new ObjectMapper());

        processor.processDue();

        assertThat(event.getStatus()).isEqualTo("processed");
        assertThat(tenant.getPlan()).isEqualTo("launch");
        assertThat(tenant.getMonthlyMinutesLimit()).isEqualTo(100);
        assertThat(account.getStatus()).isEqualTo("active");
        assertThat(account.getEnforcementMode()).isEqualTo("observe");
        var subscription = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(subscriptions).save(subscription.capture());
        assertThat(subscription.getValue().getProviderStatus()).isEqualTo("active");
    }

    private String payload(UUID tenantId) {
        return """
                {
                  "meta":{"event_name":"subscription_created","custom_data":{"tenant_id":"%s"}},
                  "data":{"type":"subscriptions","id":"sub-1","attributes":{
                    "customer_id":10,"order_id":20,"product_id":30,"variant_id":101,
                    "status":"active","test_mode":true,
                    "renews_at":"2026-09-03T00:00:00Z","ends_at":null,"trial_ends_at":null,
                    "updated_at":"2026-08-03T00:00:00Z",
                    "card_brand":"visa","card_last_four":"4242",
                    "urls":{"update_payment_method":"https://example.lemonsqueezy.com/billing/update"}
                  }}
                }
                """.formatted(tenantId);
    }
}
