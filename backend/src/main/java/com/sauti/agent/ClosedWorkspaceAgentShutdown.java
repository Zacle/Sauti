package com.sauti.agent;

import com.sauti.tenant.application.WorkspaceClosed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Stops a closed workspace's phone service.
 *
 * <p>Agents are switched off inside the closing transaction, so no call is answered for a closed
 * workspace. Phone numbers are then released to the carrier after commit; a release that fails is
 * retried on a schedule until the number is gone, so the workspace stops paying for it.
 */
@Component
class ClosedWorkspaceAgentShutdown {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClosedWorkspaceAgentShutdown.class);

    private final AgentRepository agents;
    private final TelephonyProvider telephony;
    private final TransactionTemplate newTransaction;

    ClosedWorkspaceAgentShutdown(AgentRepository agents, TelephonyProvider telephony, PlatformTransactionManager transactions) {
        this.agents = agents;
        this.telephony = telephony;
        // Runs after the closing transaction has committed, so each save needs a transaction of its own.
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener
    void deactivateAgents(WorkspaceClosed event) {
        agents.findAllByTenantIdOrderByCreatedAtDesc(event.workspaceId()).forEach(agent -> {
            agent.deactivate();
            agents.save(agent);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void releaseNumbersAfterClose(WorkspaceClosed event) {
        releasePendingNumbers();
    }

    /** Retries releases that failed, for example because the carrier was unreachable. */
    @Scheduled(fixedDelayString = "${sauti.workspace-shutdown.retry-delay-ms:600000}",
            initialDelayString = "${sauti.workspace-shutdown.initial-delay-ms:120000}")
    void retryPendingReleases() {
        releasePendingNumbers();
    }

    /** The carrier call happens outside any database transaction; only the bookkeeping is transactional. */
    void releasePendingNumbers() {
        var pending = newTransaction.execute(status -> agents.findAllHoldingNumbersInClosedWorkspaces());
        for (var agent : pending == null ? java.util.List.<Agent>of() : pending) {
            try {
                telephony.releasePhoneNumber(agent.getTwilioPhoneNumber());
                newTransaction.executeWithoutResult(status -> agents.findById(agent.getId()).ifPresent(current -> {
                    current.releasePhoneNumber();
                    agents.save(current);
                }));
            } catch (RuntimeException exception) {
                LOGGER.warn("Phone number release for a closed workspace failed; will retry exception={}",
                        exception.getClass().getSimpleName());
            }
        }
    }
}
