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
    private final BillingAccessPolicy policy = new BillingAccessPolicy(accounts, subscriptions);

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
