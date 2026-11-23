package com.sauti.billing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether a workspace may start new AI conversations (calls, browser tests, outbound calls, WhatsApp
 * replies). Two things stop it:
 * <ul>
 *   <li>{@code unpaid}: an enforced subscription that has ended or needs payment;</li>
 *   <li>{@code over_limit}: the minutes for the current cycle, or the one-off trial minutes, are used.</li>
 * </ul>
 * A call already in progress is never cut off; its agent's maximum call length still applies.
 *
 * <p>Messages are checked apart from calls ({@link #automatedMessages}): running out of minutes doesn't
 * stop messages, and running out of messages doesn't stop calls. Only messages Sauti sends on its own
 * (AI replies, the SMS tool, integration confirmations) are held at the message limit; a person replying
 * from the inbox can always send.
 */
@Service
public class BillingAccessPolicy {
    private final BillingAccountRepository accounts;
    private final BillingSubscriptionRepository subscriptions;
    private final WorkspaceUsageService usage;

    public BillingAccessPolicy(BillingAccountRepository accounts,
                               BillingSubscriptionRepository subscriptions,
                               WorkspaceUsageService usage) {
        this.accounts = accounts;
        this.subscriptions = subscriptions;
        this.usage = usage;
    }

    @Transactional(readOnly = true)
    public AccessDecision decision(UUID tenantId) {
        var account = accounts.findByTenantId(tenantId).orElse(null);
        var subscription = subscriptions.findByTenantId(tenantId).orElse(null);
        var paidThrough = subscription == null ? null : subscription.getRenewsAt();
        if (account != null && account.isEnforced()
                && (subscription == null || !subscription.permitsPaidAccessAt(OffsetDateTime.now(ZoneOffset.UTC)))) {
            var status = subscription == null ? account.getStatus() : subscription.getProviderStatus();
            return new AccessDecision(false, status, paidThrough, "unpaid");
        }
        var status = account == null || !account.isEnforced() ? "observe" : subscription.getProviderStatus();
        if (usage.current(tenantId).exhausted()) {
            return new AccessDecision(false, status, paidThrough, "over_limit");
        }
        return new AccessDecision(true, status, paidThrough, null);
    }

    /**
     * Whether Sauti may send a message on its own. Blocked when the subscription is unpaid
     * ({@code unpaid}) or the cycle's messages are used ({@code messages_over_limit}).
     */
    @Transactional(readOnly = true)
    public AccessDecision automatedMessages(UUID tenantId) {
        var account = accounts.findByTenantId(tenantId).orElse(null);
        var subscription = subscriptions.findByTenantId(tenantId).orElse(null);
        var paidThrough = subscription == null ? null : subscription.getRenewsAt();
        if (account != null && account.isEnforced()
                && (subscription == null || !subscription.permitsPaidAccessAt(OffsetDateTime.now(ZoneOffset.UTC)))) {
            var status = subscription == null ? account.getStatus() : subscription.getProviderStatus();
            return new AccessDecision(false, status, paidThrough, "unpaid");
        }
        var status = account == null || !account.isEnforced() ? "observe" : subscription.getProviderStatus();
        if (usage.current(tenantId).messagesExhausted()) {
            return new AccessDecision(false, status, paidThrough, "messages_over_limit");
        }
        return new AccessDecision(true, status, paidThrough, null);
    }

    /** Why an automated message wasn't sent, in words an agent can pass on or an owner can read. */
    @Transactional(readOnly = true)
    public String automatedMessagesBlockedReason(UUID tenantId) {
        var decision = automatedMessages(tenantId);
        if (decision.allowed()) return null;
        if ("unpaid".equals(decision.blockedReason())) {
            return "Messages are paused because this workspace's subscription has ended or requires payment.";
        }
        var current = usage.current(tenantId);
        return current.trial()
                ? "This workspace has used its " + current.includedMessages() + " trial messages."
                : "This workspace has used all " + current.includedMessages() + " messages for this billing cycle.";
    }

    @Transactional(readOnly = true)
    public void requirePaidCommunication(UUID tenantId) {
        var decision = decision(tenantId);
        if (decision.allowed()) return;
        if ("over_limit".equals(decision.blockedReason())) {
            var current = usage.current(tenantId);
            throw new PaidAccessRequiredException(current.trial()
                    ? "This workspace has used its " + current.includedMinutes()
                            + " trial minutes. Choose a plan to keep taking AI calls."
                    : "This workspace has used all " + current.includedMinutes()
                            + " minutes for this billing cycle. Upgrade the plan to keep taking AI calls.");
        }
        throw new PaidAccessRequiredException(
                "This workspace subscription has ended or requires payment. Reactivate a plan to start new AI calls."
        );
    }

    /**
     * @param blockedReason {@code unpaid}, {@code over_limit}, {@code messages_over_limit}, or null when allowed
     */
    public record AccessDecision(boolean allowed, String status, OffsetDateTime paidThrough, String blockedReason) { }
}
