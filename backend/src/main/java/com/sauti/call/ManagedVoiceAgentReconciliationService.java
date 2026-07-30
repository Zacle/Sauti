package com.sauti.call;

import com.sauti.agent.AgentConfigurationChanged;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class ManagedVoiceAgentReconciliationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            ManagedVoiceAgentReconciliationService.class
    );
    private final ManagedVoiceAgentPreparationWorker worker;
    private final ManagedVoiceAgentProvisioningService provisioning;
    private final ConcurrentLinkedQueue<AgentConfigurationChanged> pending =
            new ConcurrentLinkedQueue<>();
    private final Set<AgentConfigurationChanged> queued = ConcurrentHashMap.newKeySet();

    public ManagedVoiceAgentReconciliationService(
            ManagedVoiceAgentPreparationWorker worker,
            ManagedVoiceAgentProvisioningService provisioning
    ) {
        this.worker = worker;
        this.provisioning = provisioning;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void agentChanged(AgentConfigurationChanged changed) {
        enqueue(changed);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        enqueueAll();
    }

    @Scheduled(
            initialDelayString = "${sauti.telnyx.agent-preparation-initial-delay-ms:1000}",
            fixedDelayString = "${sauti.telnyx.agent-preparation-worker-delay-ms:250}"
    )
    public void prepareNext() {
        if (!provisioning.isConfigured()) return;
        var changed = pending.poll();
        if (changed == null) return;
        queued.remove(changed);
        try {
            worker.prepare(changed);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Managed voice agent background preparation failed agentId={} exception={}",
                    changed.agentId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    @Scheduled(
            initialDelayString = "${sauti.telnyx.agent-reconciliation-initial-delay-ms:60000}",
            fixedDelayString = "${sauti.telnyx.agent-reconciliation-delay-ms:60000}"
    )
    public void enqueueAll() {
        if (!provisioning.isConfigured()) return;
        try {
            worker.allAgents().forEach(this::enqueue);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Managed voice agent reconciliation scan failed exception={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void enqueue(AgentConfigurationChanged changed) {
        if (changed == null || changed.agentId() == null || changed.tenantId() == null) return;
        if (queued.add(changed)) pending.add(changed);
    }
}
