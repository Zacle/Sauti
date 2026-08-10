package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BillingPlanChangeServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
    private final BillingPlanChangeRequestRepository requests = mock(BillingPlanChangeRequestRepository.class);
    private final BillingPlanChangeService service = new BillingPlanChangeService(
            tenants, subscriptions, requests);

    @Test
    void recordsOneTenantScopedRequestWithoutCreatingAnotherWhopCheckout() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var renewal = OffsetDateTime.parse("2026-09-10T10:00:00Z");
        var subscription = subscription(tenant, renewal);
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));
        when(requests.findByTenantId(tenant.getId())).thenReturn(Optional.empty());
        when(requests.save(any(BillingPlanChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.request(tenant.getId(),
                new BillingPlanChangeService.PlanChangeCommand("scale", "annual"));

        assertThat(result.status()).isEqualTo("requested");
        assertThat(result.currentPlan()).isEqualTo("growth");
        assertThat(result.targetPlan()).isEqualTo("scale");
        assertThat(result.targetInterval()).isEqualTo("annual");
        assertThat(result.effectiveAt()).isEqualTo(renewal);
        assertThat(result.authorizationUrl()).isEqualTo("https://whop.com/billing/manage/mem_growth");
    }

    @Test
    void refusesARequestForTheCurrentPlanAndInterval() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription(tenant, null)));

        assertThatThrownBy(() -> service.request(tenant.getId(),
                new BillingPlanChangeService.PlanChangeCommand("growth", "monthly")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already on");
    }

    private BillingSubscription subscription(Tenant tenant, OffsetDateTime renewal) {
        var subscription = new BillingSubscription(tenant.getId(), "whop", "mem_growth");
        subscription.synchronize("user_1", "mem_growth", "prod_sauti", "plan_growth_monthly",
                "growth", "monthly", "active", true, renewal, null, null,
                OffsetDateTime.parse("2026-08-10T10:00:00Z"), "", "",
                "https://whop.com/billing/manage/mem_growth");
        return subscription;
    }
}
