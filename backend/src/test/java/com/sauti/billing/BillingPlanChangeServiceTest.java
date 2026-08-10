package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private final WhopPlanCatalog plans = new WhopPlanCatalog(
            "plan_launch_monthly", "plan_launch_annual", "plan_growth_monthly", "plan_growth_annual",
            "plan_scale_monthly", "plan_scale_annual");
    private final WhopPlanChangeGateway whop = mock(WhopPlanChangeGateway.class);
    private final BillingLedgerService ledger = mock(BillingLedgerService.class);
    private final BillingPlanChangeService service = new BillingPlanChangeService(
            tenants, subscriptions, requests, plans, whop, ledger);

    @Test
    void recordsOneTenantScopedRequestWithoutCreatingAnotherWhopCheckout() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var renewal = OffsetDateTime.parse("2026-09-10T10:00:00Z");
        var subscription = subscription(tenant, renewal);
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));
        when(requests.findByTenantId(tenant.getId())).thenReturn(Optional.empty());
        when(requests.save(any(BillingPlanChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(whop.prepare(subscription, plans.checkoutSelection("scale", "annual"), renewal))
                .thenReturn(WhopPlanChangeGateway.PlanTransition.scheduled("inv_change", "plan_generated"));

        var result = service.request(tenant.getId(),
                new BillingPlanChangeService.PlanChangeCommand("scale", "annual"));

        assertThat(result.status()).isEqualTo("scheduled");
        assertThat(result.currentPlan()).isEqualTo("growth");
        assertThat(result.targetPlan()).isEqualTo("scale");
        assertThat(result.targetInterval()).isEqualTo("annual");
        assertThat(result.effectiveAt()).isEqualTo(renewal);
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

    @Test
    void adoptsOneVerifiedExistingTargetMembershipInsteadOfCreatingAnother() throws Exception {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var renewal = OffsetDateTime.parse("2026-09-10T10:00:00Z");
        var subscription = subscription(tenant, renewal);
        var account = new BillingAccount(tenant.getId());
        var replacement = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"id":"mem_scale_existing","status":"active","cancel_at_period_end":false,
                 "updated_at":"2026-08-10T11:00:00Z","renewal_period_end":"2026-09-10T11:00:00Z",
                 "user":{"id":"user_1"},"product":{"id":"prod_sauti"},
                 "manage_url":"https://whop.com/billing/manage/mem_scale_existing"}
                """);
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));
        when(requests.findByTenantId(tenant.getId())).thenReturn(Optional.empty());
        when(requests.save(any(BillingPlanChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(whop.prepare(subscription, plans.checkoutSelection("scale", "monthly"), renewal))
                .thenReturn(WhopPlanChangeGateway.PlanTransition.adopt(replacement));
        when(ledger.account(tenant.getId())).thenReturn(account);

        var result = service.request(tenant.getId(),
                new BillingPlanChangeService.PlanChangeCommand("scale", "monthly"));

        assertThat(result.status()).isEqualTo("completed");
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("mem_scale_existing");
        assertThat(subscription.getPlan()).isEqualTo("scale");
        verify(subscriptions).save(subscription);
        verify(tenants).save(tenant);
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
