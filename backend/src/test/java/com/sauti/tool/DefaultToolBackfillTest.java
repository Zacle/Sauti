package com.sauti.tool;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class DefaultToolBackfillTest {

    @Test
    void seedsDefaultsForExistingAgentsAtStartup() {
        var agentRepository = mock(AgentRepository.class);
        var defaultToolSeeder = mock(DefaultToolSeeder.class);
        var firstAgent = mock(Agent.class);
        var secondAgent = mock(Agent.class);
        when(agentRepository.findAll()).thenReturn(List.of(firstAgent, secondAgent));

        new DefaultToolBackfill(agentRepository, defaultToolSeeder)
                .run(mock(ApplicationArguments.class));

        verify(defaultToolSeeder).seedDefaults(firstAgent);
        verify(defaultToolSeeder).seedDefaults(secondAgent);
    }
}
