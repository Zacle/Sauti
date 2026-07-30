package com.sauti.call;

import com.sauti.agent.AgentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps agent loading, primary-language greeting resolution, and managed
 * binding lookup inside one read transaction. Primary-language greetings may
 * access lazy tenant or agent configuration that must not be detached first.
 */
@Service
public class BrowserVoiceRuntimePreparationService {
    private final AgentRepository agents;
    private final CallPipelineService callPipeline;
    private final TelnyxAiBrowserVoiceRuntimeService telnyxRuntime;

    public BrowserVoiceRuntimePreparationService(
            AgentRepository agents,
            CallPipelineService callPipeline,
            TelnyxAiBrowserVoiceRuntimeService telnyxRuntime
    ) {
        this.agents = agents;
        this.callPipeline = callPipeline;
        this.telnyxRuntime = telnyxRuntime;
    }

    @Transactional(readOnly = true)
    public BrowserVoiceRuntimeSession prepare(
            UUID tenantId,
            UUID agentId,
            String requestedVoice,
            String language
    ) {
        var agent = agents.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        var requested = requestedVoice == null ? "" : requestedVoice.trim();
        var saved = agent.getTtsVoiceId() == null ? "" : agent.getTtsVoiceId().trim();
        if (!requested.isBlank() && !requested.equals(saved)) {
            throw new IllegalArgumentException(
                    "Save the selected Telnyx voice before preparing the test call"
            );
        }
        var greeting = callPipeline.managedVoiceGreeting(agent, language);
        return telnyxRuntime.prepare(agent, greeting, language);
    }
}
