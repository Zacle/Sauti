package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.tenant.TenantRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final BillingLedgerService ledger = mock(BillingLedgerService.class);
    private final ProviderCostReconciliationRepository jobs = mock(ProviderCostReconciliationRepository.class);
    private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
    private final BillingAddOnSubscriptionRepository addOnSubscriptions = mock(BillingAddOnSubscriptionRepository.class);
    private final BillingPlanChangeRequestRepository planChangeRequests = mock(BillingPlanChangeRequestRepository.class);
    private final BillingService service = new BillingService(tenants, ledger, jobs, subscriptions,
            addOnSubscriptions, planChangeRequests);

    @Test
    void reportsNetEvidenceTotalsAndTenantScopedReconciliationHealth() {
        var tenantId = UUID.randomUUID();
        var account = new BillingAccount(tenantId);
        var accountId = account.getId();
        var entries = List.of(
                entry(tenantId, accountId, "debit", "voice_provider_cost", BigDecimal.ONE,
                        "provider_charge", new BigDecimal("2.00"), "USD", "provider_confirmed", "confirmed"),
                entry(tenantId, accountId, "credit", "voice_provider_cost", BigDecimal.ONE,
                        "provider_charge", new BigDecimal("0.50"), "USD", "provider_confirmed", "correction"),
                entry(tenantId, accountId, "debit", "voice_call", new BigDecimal("2.00"),
                        "minute", null, null, "unpriced", "usage"),
                entry(tenantId, accountId, "credit", "voice_call", new BigDecimal("0.50"),
                        "minute", null, null, "unpriced", "usage-correction")
        );
        var reconciled = mock(ProviderCostReconciliationJob.class);
        var retrying = mock(ProviderCostReconciliationJob.class);
        when(reconciled.getStatus()).thenReturn("reconciled");
        when(retrying.getStatus()).thenReturn("retrying");
        when(ledger.account(tenantId)).thenReturn(account);
        when(ledger.balances(tenantId)).thenReturn(Map.of("USD", new BigDecimal("-1.50")));
        when(ledger.currentCycle(tenantId)).thenReturn(entries);
        when(ledger.recent(tenantId)).thenReturn(entries);
        when(jobs.findAllByTenantId(tenantId)).thenReturn(List.of(reconciled, retrying));
        when(subscriptions.findByTenantId(tenantId)).thenReturn(java.util.Optional.empty());
        when(addOnSubscriptions.findAllByTenantId(tenantId)).thenReturn(List.of());

        var response = service.account(tenantId);

        assertThat(response.costTotals()).containsExactly(
                new BillingDtos.CostTotalResponse("provider_confirmed", "USD", new BigDecimal("1.50")));
        assertThat(response.unpricedUsage()).containsExactly(
                new BillingDtos.UnpricedUsageResponse("voice_call", "minute", new BigDecimal("1.50")));
        assertThat(response.reconciliation().reconciled()).isEqualTo(1);
        assertThat(response.reconciliation().retrying()).isEqualTo(1);
        assertThat(response.reconciliation().unavailable()).isZero();
    }

    @Test
    void identifiesTheExactProviderMembershipSynchronizedWithTheWorkspace() {
        var tenantId = UUID.randomUUID();
        var account = new BillingAccount(tenantId);
        var subscription = new BillingSubscription(tenantId, "whop", "mem_keep_this_one");
        subscription.synchronize("user_1", "order_1", "product_1", "plan_1", "scale", "monthly",
                "active", true, OffsetDateTime.now().plusMonths(1), null, null, OffsetDateTime.now(),
                "visa", "4242", "https://whop.com/billing/manage/mem_keep_this_one");
        when(ledger.account(tenantId)).thenReturn(account);
        when(ledger.balances(tenantId)).thenReturn(Map.of());
        when(ledger.currentCycle(tenantId)).thenReturn(List.of());
        when(ledger.recent(tenantId)).thenReturn(List.of());
        when(jobs.findAllByTenantId(tenantId)).thenReturn(List.of());
        when(subscriptions.findByTenantId(tenantId)).thenReturn(java.util.Optional.of(subscription));
        when(addOnSubscriptions.findAllByTenantId(tenantId)).thenReturn(List.of());

        var response = service.account(tenantId);

        assertThat(response.subscription().providerReference()).isEqualTo("mem_keep_this_one");
        assertThat(response.subscription().plan()).isEqualTo("scale");
    }

    private CommunicationLedgerEntry entry(UUID tenantId, UUID accountId, String direction, String category,
                                           BigDecimal quantity, String unit, BigDecimal amount, String currency,
                                           String basis, String key) {
        return new CommunicationLedgerEntry(
                tenantId, accountId, direction, category, quantity, unit, amount, currency,
                key, key, "Test billing evidence", basis, "{}"
        );
    }
}
