package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingAccessPolicyTest {
    private final BillingAccountRepository accounts = mock(BillingAccountRepository.class);
    private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
    private final WorkspaceUsageService usage = mock(WorkspaceUsageService.class);
    private final BillingAccessPolicy policy = new BillingAccessPolicy(accounts, subscriptions, usage);

    @org.junit.jupiter.api.BeforeEach
    void minutesLeft() {
        when(usage.current(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(usage(false, 750, 10));
    }

    @Test
    void keepsScheduledCancellationAvailableThroughThePaidPeriod() {
        var tenantId = UUID.randomUUID();
        var account = account(tenantId, "active", "enforce");
        var subscription = subscription(tenantId, "canceling", OffsetDateTime.now().plusDays(5));
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.of(account));
        when(subscriptions.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        assertThat(policy.decision(tenantId).allowed()).isTrue();
    }

    @Test
    void blocksNewCallsAfterThePaidPeriodEnds() {
        var tenantId = UUID.randomUUID();
        var account = account(tenantId, "cancelled", "enforce");
        var subscription = subscription(tenantId, "canceled", OffsetDateTime.now().minusSeconds(1));
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.of(account));
        when(subscriptions.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> policy.requirePaidCommunication(tenantId))
                .isInstanceOf(PaidAccessRequiredException.class)
                .hasMessageContaining("Reactivate a plan");
    }

    @Test
    void preservesObserveModeForUnmigratedPilotWorkspaces() {
        var tenantId = UUID.randomUUID();
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.of(account(tenantId, "preview", "observe")));

        assertThat(policy.decision(tenantId).allowed()).isTrue();
    }

    @Test
    void blocksNewCallsOnceThePaidCycleMinutesAreUsed() {
        var tenantId = UUID.randomUUID();
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.of(account(tenantId, "active", "enforce")));
        when(subscriptions.findByTenantId(tenantId))
                .thenReturn(Optional.of(subscription(tenantId, "active", OffsetDateTime.now().plusDays(9))));
        when(usage.current(tenantId)).thenReturn(usage(false, 750, 750));

        var decision = policy.decision(tenantId);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockedReason()).isEqualTo("over_limit");
        assertThatThrownBy(() -> policy.requirePaidCommunication(tenantId))
                .isInstanceOf(PaidAccessRequiredException.class)
                .hasMessageContaining("all 750 minutes for this billing cycle");
    }

    @Test
    void aTrialStopsAtItsOneOffAllowanceEvenWithoutAnEnforcedAccount() {
        var tenantId = UUID.randomUUID();
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.of(account(tenantId, "preview", "observe")));
        when(usage.current(tenantId)).thenReturn(usage(true, 60, 61));

        assertThat(policy.decision(tenantId).blockedReason()).isEqualTo("over_limit");
        assertThatThrownBy(() -> policy.requirePaidCommunication(tenantId))
                .hasMessageContaining("used its 60 trial minutes");
    }

    @Test
    void anUnpaidWorkspaceIsBlockedForPaymentFirst() {
        var tenantId = UUID.randomUUID();
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.of(account(tenantId, "past_due", "enforce")));
        when(subscriptions.findByTenantId(tenantId))
                .thenReturn(Optional.of(subscription(tenantId, "past_due", OffsetDateTime.now().minusDays(1))));
        when(usage.current(tenantId)).thenReturn(usage(false, 750, 900));

        assertThat(policy.decision(tenantId).blockedReason()).isEqualTo("unpaid");
    }

    private static WorkspaceUsageService.Usage usage(boolean trial, int plan, int used) {
        return new WorkspaceUsageService.Usage(trial, plan, 0, used, OffsetDateTime.now().minusDays(3),
                trial ? null : OffsetDateTime.now().plusDays(27));
    }

    private BillingAccount account(UUID tenantId, String status, String mode) {
        var account = new BillingAccount(tenantId);
        account.configure(status, mode, "USD", null, java.math.BigDecimal.ZERO);
        return account;
    }

    private BillingSubscription subscription(UUID tenantId, String status, OffsetDateTime paidThrough) {
        var subscription = new BillingSubscription(tenantId, "whop", "mem_1");
        subscription.synchronize("user_1", "mem_1", "prod_1", "plan_1", "growth", "monthly",
                status, false, paidThrough, paidThrough, null, OffsetDateTime.now(), "", "", "");
        return subscription;
    }
}
