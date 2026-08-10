package com.sauti.billing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingAccessPolicy {
    private final BillingAccountRepository accounts;
    private final BillingSubscriptionRepository subscriptions;

    public BillingAccessPolicy(BillingAccountRepository accounts,
                               BillingSubscriptionRepository subscriptions) {
        this.accounts = accounts;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public AccessDecision decision(UUID tenantId) {
        var account = accounts.findByTenantId(tenantId).orElse(null);
        if (account == null || !account.isEnforced()) {
            return new AccessDecision(true, "observe", null);
        }
        var subscription = subscriptions.findByTenantId(tenantId).orElse(null);
        if (subscription != null && subscription.permitsPaidAccessAt(OffsetDateTime.now(ZoneOffset.UTC))) {
            return new AccessDecision(true, subscription.getProviderStatus(), subscription.getRenewsAt());
        }
        var status = subscription == null ? account.getStatus() : subscription.getProviderStatus();
        return new AccessDecision(false, status, subscription == null ? null : subscription.getRenewsAt());
    }

    @Transactional(readOnly = true)
    public void requirePaidCommunication(UUID tenantId) {
        if (!decision(tenantId).allowed()) {
            throw new PaidAccessRequiredException(
                    "This workspace subscription has ended or requires payment. Reactivate a plan to start new AI calls."
            );
        }
    }

    public record AccessDecision(boolean allowed, String status, OffsetDateTime paidThrough) { }
}
