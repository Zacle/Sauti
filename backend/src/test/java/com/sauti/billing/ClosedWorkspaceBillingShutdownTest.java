package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ClosedWorkspaceBillingShutdownTest {
    private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
    private final BillingAddOnSubscriptionRepository addOns = mock(BillingAddOnSubscriptionRepository.class);
    private final BillingCheckoutService providers = mock(BillingCheckoutService.class);
    private final ClosedWorkspaceBillingShutdown shutdown =
            new ClosedWorkspaceBillingShutdown(subscriptions, addOns, providers, new NoopTransactions());
    private final UUID workspace = UUID.randomUUID();

    @Test
    void cancelsThePlanAndAddOnsAndRecordsItSoItIsNeverSentTwice() throws Exception {
        var plan = subscription("active");
        var addOn = new BillingAddOnSubscription(workspace, "whop", "mem_addon_1");
        addOn.synchronize("plan_x", "extra_minutes", "active", false, null, null, OffsetDateTime.now(), null);
        when(subscriptions.findUncancelledInClosedWorkspaces()).thenReturn(List.of(plan));
        when(subscriptions.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(addOns.findUncancelledInClosedWorkspaces()).thenReturn(List.of(addOn));
        when(addOns.findById(addOn.getId())).thenReturn(Optional.of(addOn));

        shutdown.cancelPending();

        verify(providers).cancelWith("whop", workspace);
        verify(providers).cancelAddOnWith("whop", workspace, "mem_addon_1");
        assertThat(plan.stillRenews()).isFalse();
        assertThat(addOn.stillRenews()).isFalse();
    }

    @Test
    void aProviderFailureLeavesThePlanToBeRetried() throws Exception {
        var plan = subscription("active");
        when(subscriptions.findUncancelledInClosedWorkspaces()).thenReturn(List.of(plan));
        when(addOns.findUncancelledInClosedWorkspaces()).thenReturn(List.of());
        when(providers.cancelWith("whop", workspace)).thenThrow(new IllegalStateException("Whop is down"));

        shutdown.cancelPending();

        assertThat(plan.stillRenews()).isTrue();
        verify(subscriptions, never()).save(any());
    }

    @Test
    void aPlanAlreadyCancellingIsMarkedWithoutCallingTheProvider() throws Exception {
        var plan = subscription("canceling");
        when(subscriptions.findUncancelledInClosedWorkspaces()).thenReturn(List.of(plan));
        when(subscriptions.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(addOns.findUncancelledInClosedWorkspaces()).thenReturn(List.of());

        shutdown.cancelPending();

        verify(providers, never()).cancelWith(any(), any());
        verify(subscriptions, times(1)).save(plan);
    }

    private BillingSubscription subscription(String status) throws Exception {
        var plan = new BillingSubscription(workspace, "whop", "mem_plan_1");
        Field field = BillingSubscription.class.getDeclaredField("providerStatus");
        field.setAccessible(true);
        field.set(plan, status);
        return plan;
    }

    private static final class NoopTransactions implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
