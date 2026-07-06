package com.sauti.billing;

import com.sauti.tenant.Tenant;

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
}
