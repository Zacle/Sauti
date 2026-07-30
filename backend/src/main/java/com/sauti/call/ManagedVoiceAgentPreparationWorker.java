package com.sauti.call;

import com.sauti.agent.AgentConfigurationChanged;
import com.sauti.agent.AgentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagedVoiceAgentPreparationWorker {
    private final AgentRepository agents;
    private final ManagedVoiceAgentProvisioningService provisioning;
    private final CallPipelineService callPipeline;

    public ManagedVoiceAgentPreparationWorker(
            AgentRepository agents,
            ManagedVoiceAgentProvisioningService provisioning,
            CallPipelineService callPipeline
    ) {
        this.agents = agents;
        this.provisioning = provisioning;
        this.callPipeline = callPipeline;
    }

    @Transactional(readOnly = true)
    public List<AgentConfigurationChanged> allAgents() {
        return agents.findAll().stream()
                .map(agent -> new AgentConfigurationChanged(
                        agent.getTenant().getId(),
                        agent.getId()
                ))
                .toList();
    }

    @Transactional
    public void prepare(AgentConfigurationChanged changed) {
        agents.findByIdAndTenantId(changed.agentId(), changed.tenantId())
                .ifPresent(agent -> provisioning.synchronizeAll(
                        agent,
                        language -> callPipeline.managedVoiceGreeting(agent, language)
                ));
    }
}
