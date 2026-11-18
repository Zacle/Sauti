package com.sauti.billing;

import com.sauti.tenant.application.WorkspaceClosed;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stops billing for a closed workspace: the plan and every add-on are cancelled with the provider
 * at the end of the paid period, so the customer keeps what they paid for and is not charged again.
 *
 * <p>Runs right after the workspace closes and again on a schedule, so a provider outage only
 * delays the cancellation. Each subscription records that its cancellation was accepted, so it is
 * never requested twice.
 */
@Component
class ClosedWorkspaceBillingShutdown {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClosedWorkspaceBillingShutdown.class);

    private final BillingSubscriptionRepository subscriptions;
    private final BillingAddOnSubscriptionRepository addOns;
    private final BillingCheckoutService providers;
    private final TransactionTemplate newTransaction;

    ClosedWorkspaceBillingShutdown(BillingSubscriptionRepository subscriptions, BillingAddOnSubscriptionRepository addOns,
                                   BillingCheckoutService providers, PlatformTransactionManager transactions) {
        this.subscriptions = subscriptions;
        this.addOns = addOns;
        this.providers = providers;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void afterWorkspaceClosed(WorkspaceClosed event) {
        cancelPending();
    }

    @Scheduled(fixedDelayString = "${sauti.workspace-shutdown.retry-delay-ms:600000}",
            initialDelayString = "${sauti.workspace-shutdown.initial-delay-ms:120000}")
    void retryPending() {
        cancelPending();
    }

    /** Provider calls run outside database transactions; only the "requested" marker is saved transactionally. */
    void cancelPending() {
        var plans = newTransaction.execute(status -> subscriptions.findUncancelledInClosedWorkspaces());
        for (var plan : plans == null ? List.<BillingSubscription>of() : plans) {
            if (!plan.stillRenews()) {
                markPlan(plan);
                continue;
            }
            try {
                providers.cancelWith(plan.getProvider(), plan.getTenantId());
                markPlan(plan);
            } catch (RuntimeException exception) {
                LOGGER.warn("Plan cancellation for a closed workspace failed; will retry provider={} exception={}",
                        plan.getProvider(), exception.getClass().getSimpleName());
            }
        }
        var extras = newTransaction.execute(status -> addOns.findUncancelledInClosedWorkspaces());
        for (var addOn : extras == null ? List.<BillingAddOnSubscription>of() : extras) {
            if (!addOn.stillRenews()) {
                markAddOn(addOn);
                continue;
            }
            try {
                providers.cancelAddOnWith(addOn.getProvider(), addOn.getTenantId(), addOn.getProviderSubscriptionId());
                markAddOn(addOn);
            } catch (RuntimeException exception) {
                LOGGER.warn("Add-on cancellation for a closed workspace failed; will retry provider={} exception={}",
                        addOn.getProvider(), exception.getClass().getSimpleName());
            }
        }
    }

    private void markPlan(BillingSubscription plan) {
        newTransaction.executeWithoutResult(status -> subscriptions.findById(plan.getId()).ifPresent(current -> {
            current.markClosureCancelRequested(now());
            subscriptions.save(current);
        }));
    }

    private void markAddOn(BillingAddOnSubscription addOn) {
        newTransaction.executeWithoutResult(status -> addOns.findById(addOn.getId()).ifPresent(current -> {
            current.markClosureCancelRequested(now());
            addOns.save(current);
        }));
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
