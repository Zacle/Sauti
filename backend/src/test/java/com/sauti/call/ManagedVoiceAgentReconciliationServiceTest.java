package com.sauti.call;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.AgentConfigurationChanged;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManagedVoiceAgentReconciliationServiceTest {
    @Test
    void preparesAnAgentQueuedAfterItsConfigurationCommits() {
        var worker = mock(ManagedVoiceAgentPreparationWorker.class);
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
        when(provisioning.isConfigured()).thenReturn(true);
        var service = new ManagedVoiceAgentReconciliationService(worker, provisioning);
        var changed = new AgentConfigurationChanged(UUID.randomUUID(), UUID.randomUUID());

        service.agentChanged(changed);
        service.prepareNext();

        verify(worker).prepare(changed);
    }

    @Test
    void queuesExistingAgentsForBackgroundReconciliationAtStartup() {
        var worker = mock(ManagedVoiceAgentPreparationWorker.class);
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
        var changed = new AgentConfigurationChanged(UUID.randomUUID(), UUID.randomUUID());
        when(provisioning.isConfigured()).thenReturn(true);
        when(worker.allAgents()).thenReturn(List.of(changed));
        var service = new ManagedVoiceAgentReconciliationService(worker, provisioning);

        service.applicationReady();
        service.prepareNext();

        verify(worker).prepare(changed);
    }
}
