package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultToolSeederTest {
    @Test
    void activatesBookingToolsAndPreservesTheConnectedGoogleCredential() {
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        var credentialId = UUID.randomUUID();
        when(agent.getId()).thenReturn(agentId);
        when(agent.isBookingEnabled()).thenReturn(true);
        var availability = tool(agent, "check_availability");
        var booking = tool(agent, "book_slot");
        availability.connectCalendar("google", credentialId);
        booking.connectCalendar("google", credentialId);
        availability.deactivate();
        booking.deactivate();
        var repository = mock(AgentToolRepository.class);
        when(repository.findByAgent_IdOrderByDisplayOrderAsc(agentId))
                .thenReturn(List.of(availability, booking));

        new DefaultToolSeeder(repository).synchronizeCapabilities(agent);

        assertThat(availability.isActive()).isTrue();
        assertThat(booking.isActive()).isTrue();
        assertThat(availability.getCalendarType()).isEqualTo("google");
        assertThat(availability.getCalendarCredentialId()).isEqualTo(credentialId);
        assertThat(booking.getCalendarCredentialId()).isEqualTo(credentialId);
    }

    @Test
    void deactivatesBookingToolsWhenBookingIsDisabledWithoutDisconnectingGoogle() {
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        var credentialId = UUID.randomUUID();
        when(agent.getId()).thenReturn(agentId);
        when(agent.isBookingEnabled()).thenReturn(false);
        var availability = tool(agent, "check_availability");
        availability.connectCalendar("google", credentialId);
        var repository = mock(AgentToolRepository.class);
        when(repository.findByAgent_IdOrderByDisplayOrderAsc(agentId)).thenReturn(List.of(availability));

        new DefaultToolSeeder(repository).synchronizeCapabilities(agent);

        assertThat(availability.isActive()).isFalse();
        assertThat(availability.getCalendarType()).isEqualTo("google");
        assertThat(availability.getCalendarCredentialId()).isEqualTo(credentialId);
    }

    private AgentTool tool(Agent agent, String name) {
        return new AgentTool(agent, name, name, Map.of("type", "object"), "sauti_calendar", false, 10);
    }
}
