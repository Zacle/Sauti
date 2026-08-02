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
            List<LedgerEntryResponse> recentEntries
    ) {
        static BillingAccountResponse from(BillingAccount account, Map<String, BigDecimal> balances,
                                           List<CommunicationLedgerEntry> recentEntries) {
            var blocked = List.of("suspended", "cancelled").contains(account.getStatus());
            var entitledStatus = List.of("active", "trialing").contains(account.getStatus());
            var hasBalance = balances.getOrDefault(account.getBillingCurrency(), BigDecimal.ZERO).signum() > 0;
            var paidResourcesAllowed = !blocked && (!account.isEnforced() || (entitledStatus && hasBalance));
            return new BillingAccountResponse(
                    account.getId(), account.getStatus(), account.getEnforcementMode(),
                    account.getBillingCurrency(), account.getMonthlySpendingLimit(),
                    account.getLowBalanceThreshold(), balances, paidResourcesAllowed,
                    recentEntries.stream().map(LedgerEntryResponse::from).toList()
            );
        }
    }

    public record LedgerEntryResponse(
            UUID id,
            String direction,
            String category,
            BigDecimal quantity,
            String unit,
            BigDecimal amount,
            String currency,
            String externalReference,
            String description,
            OffsetDateTime createdAt
    ) {
        static LedgerEntryResponse from(CommunicationLedgerEntry entry) {
            return new LedgerEntryResponse(
                    entry.getId(), entry.getDirection(), entry.getCategory(), entry.getQuantity(), entry.getUnit(),
                    entry.getAmount(), entry.getCurrency(), entry.getExternalReference(), entry.getDescription(),
                    entry.getCreatedAt()
            );
        }
    }
}
