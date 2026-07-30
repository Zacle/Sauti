package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrowserVoiceRuntimePreparationServiceTest {
    private final AgentRepository agents = mock(AgentRepository.class);
    private final CallPipelineService callPipeline = mock(CallPipelineService.class);
    private final TelnyxAiBrowserVoiceRuntimeService runtime =
            mock(TelnyxAiBrowserVoiceRuntimeService.class);
    private final BrowserVoiceRuntimePreparationService service =
            new BrowserVoiceRuntimePreparationService(agents, callPipeline, runtime);

    @Test
    void resolvesPrimaryGreetingAndBindingInsideOneServiceBoundary() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var agent = mock(Agent.class);
        var session = new BrowserVoiceRuntimeSession(
                "telnyx", "", "", Map.of("agentId", "assistant-fr")
        );
        when(agents.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agent));
        when(agent.getTtsVoiceId()).thenReturn("Telnyx.Ultra.voice");
        when(callPipeline.managedVoiceGreeting(agent, "fr")).thenReturn("Bonjour");
        when(runtime.prepare(agent, "Bonjour", "fr")).thenReturn(session);

        assertThat(service.prepare(
                tenantId, agentId, "Telnyx.Ultra.voice", "fr"
        )).isSameAs(session);
        verify(callPipeline).managedVoiceGreeting(agent, "fr");
        verify(runtime).prepare(agent, "Bonjour", "fr");
    }

    @Test
    void rejectsAnUnsavedVoiceBeforeRuntimeLookup() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var agent = mock(Agent.class);
        when(agents.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agent));
        when(agent.getTtsVoiceId()).thenReturn("Telnyx.Ultra.saved");

        assertThatThrownBy(() -> service.prepare(
                tenantId, agentId, "Telnyx.Ultra.unsaved", "fr"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Save the selected Telnyx voice");
    }

    @Test
    void rejectsAnUnknownAgent() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(agents.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(
                tenantId, agentId, "", "fr"
        )).isInstanceOf(EntityNotFoundException.class);
    }
}
