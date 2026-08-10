package com.sauti.billing;

import com.sauti.tenant.Tenant;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {
    }

    public record BillingUsageResponse(
            String plan,
            String status,
            int monthlyMinutesLimit,
            int minutesUsedThisCycle,
            int remainingMinutes,
            int usagePercent,
            boolean limitReached
    ) {
        public static BillingUsageResponse from(Tenant tenant) {
            int remaining = Math.max(0, tenant.getMonthlyMinutesLimit() - tenant.getMinutesUsedThisCycle());
            int percent = tenant.getMonthlyMinutesLimit() == 0
                    ? 100
                    : (int) Math.round((tenant.getMinutesUsedThisCycle() * 100.0) / tenant.getMonthlyMinutesLimit());
            return new BillingUsageResponse(
                    tenant.getPlan(),
                    tenant.getStatus(),
                    tenant.getMonthlyMinutesLimit(),
                    tenant.getMinutesUsedThisCycle(),
                    remaining,
                    percent,
                    tenant.getMinutesUsedThisCycle() >= tenant.getMonthlyMinutesLimit()
            );
        }
    }

    public record BillingAccountResponse(
            UUID id,
            String status,
            String enforcementMode,
            String billingCurrency,
            BigDecimal monthlySpendingLimit,
            BigDecimal lowBalanceThreshold,
            Map<String, BigDecimal> communicationBalances,
            boolean paidResourcesAllowed,
            SubscriptionResponse subscription,
            PlanChangeResponse pendingPlanChange,
            List<AddOnResponse> addOns,
            List<CostTotalResponse> costTotals,
            List<UnpricedUsageResponse> unpricedUsage,
            ReconciliationHealthResponse reconciliation,
            List<LedgerEntryResponse> recentEntries
    ) {
        static BillingAccountResponse from(BillingAccount account, Map<String, BigDecimal> balances,
                                           BillingSubscription subscription,
                                           BillingPlanChangeRequest pendingPlanChange,
                                           List<BillingAddOnSubscription> addOnSubscriptions,
                                           List<CostTotalResponse> costTotals,
                                           List<UnpricedUsageResponse> unpricedUsage,
                                           ReconciliationHealthResponse reconciliation,
                                           List<CommunicationLedgerEntry> recentEntries) {
            var subscriptionAllowsAccess = subscription != null
                    && subscription.permitsPaidAccessAt(OffsetDateTime.now());
            var blocked = List.of("suspended", "cancelled").contains(account.getStatus())
                    || (account.isEnforced() && !subscriptionAllowsAccess);
            var entitledStatus = List.of("active", "trialing").contains(account.getStatus());
            var paidResourcesAllowed = !blocked && (!account.isEnforced()
                    || (entitledStatus && subscriptionAllowsAccess));
            return new BillingAccountResponse(
                    account.getId(), account.getStatus(), account.getEnforcementMode(),
                    account.getBillingCurrency(), account.getMonthlySpendingLimit(),
                    account.getLowBalanceThreshold(), balances, paidResourcesAllowed,
                    SubscriptionResponse.from(subscription), PlanChangeResponse.from(pendingPlanChange),
                    activeAddOns(addOnSubscriptions),
                    costTotals, unpricedUsage, reconciliation,
                    recentEntries.stream().map(LedgerEntryResponse::from).toList()
            );
        }

        private static List<AddOnResponse> activeAddOns(List<BillingAddOnSubscription> subscriptions) {
            var now = OffsetDateTime.now();
            return subscriptions.stream().filter(item -> item.activeAt(now))
                    .collect(java.util.stream.Collectors.groupingBy(
                            BillingAddOnSubscription::getAddOn,
                            java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.toList()))
                    .entrySet().stream()
                    .map(entry -> new AddOnResponse(entry.getKey(), entry.getValue().size(),
                            entry.getValue().get(0).getProviderStatus(),
                            entry.getValue().get(0).getManageUrl()))
                    .toList();
        }
    }

    public record SubscriptionResponse(String provider, String providerReference, String plan, String interval,
                                       String status, OffsetDateTime renewsAt, String manageUrl) {
        static SubscriptionResponse from(BillingSubscription subscription) {
            return subscription == null ? null : new SubscriptionResponse(
                    subscription.getProvider(), subscription.getProviderSubscriptionId(), subscription.getPlan(),
                    subscription.getBillingInterval(), subscription.getProviderStatus(), subscription.getRenewsAt(),
                    subscription.getUpdatePaymentMethodUrl());
        }
    }

    public record AddOnResponse(String addOn, int quantity, String status, String manageUrl) { }

    public record PlanChangeResponse(UUID id, String status, String currentPlan, String targetPlan,
                                     String targetInterval, OffsetDateTime effectiveAt,
                                     String collectionMethod) {
        static PlanChangeResponse from(BillingPlanChangeRequest request) {
            return request == null ? null : new PlanChangeResponse(request.getId(), request.getStatus(),
                    request.getCurrentPlan(), request.getTargetPlan(), request.getTargetInterval(),
                    request.getEffectiveAt(), request.getCollectionMethod());
        }
    }

    public record CostTotalResponse(String costBasis, String currency, BigDecimal amount) { }

    public record UnpricedUsageResponse(String category, String unit, BigDecimal quantity) { }

    public record ReconciliationHealthResponse(
            long pending,
            long retrying,
            long reconciled,
            long estimated,
            long unavailable
    ) { }

    public record LedgerEntryResponse(
            UUID id,
            String direction,
            String category,
            BigDecimal quantity,
            String unit,
            BigDecimal amount,
            String currency,
            String costBasis,
            String externalReference,
            String description,
            OffsetDateTime createdAt
    ) {
        static LedgerEntryResponse from(CommunicationLedgerEntry entry) {
            return new LedgerEntryResponse(
                    entry.getId(), entry.getDirection(), entry.getCategory(), entry.getQuantity(), entry.getUnit(),
                    entry.getAmount(), entry.getCurrency(), entry.getCostBasis(), entry.getExternalReference(), entry.getDescription(),
                    entry.getCreatedAt()
            );
        }
    }
}
