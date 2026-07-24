package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import com.sauti.agent.Agent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultToolSeederTest {
    @Test
    void seedsCrossDomainActionPoliciesAsToolMetadata() {
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(agent.getId()).thenReturn(agentId);
        when(agent.getBookingRequiredFields()).thenReturn(List.of());
        var repository = mock(AgentToolRepository.class);
        when(repository.findByAgent_IdOrderByDisplayOrderAsc(agentId)).thenReturn(List.of());

        new DefaultToolSeeder(repository).seedDefaults(agent);

        var captor = ArgumentCaptor.forClass(AgentTool.class);
        verify(repository, times(16)).save(captor.capture());
        var tools = captor.getAllValues().stream().collect(java.util.stream.Collectors.toMap(
                AgentTool::getToolName, tool -> tool
        ));
        assertThat(tools.get("lookup_google_sheet_row").actionEffect()).isEqualTo(ToolActionEffect.READ_ONLY);
        assertThat(tools.get("lookup_booking").actionEffect()).isEqualTo(ToolActionEffect.READ_ONLY);
        assertThat(tools.get("update_booking").actionEffect()).isEqualTo(ToolActionEffect.DATA_WRITE);
        assertThat(tools.get("update_booking").confirmationPolicy()).isEqualTo(ToolConfirmationPolicy.EXPLICIT);
        assertThat(tools.get("update_google_sheet_row").actionEffect()).isEqualTo(ToolActionEffect.DATA_WRITE);
        assertThat(tools.get("send_whatsapp_message").actionEffect()).isEqualTo(ToolActionEffect.EXTERNAL_COMMUNICATION);
        assertThat(tools.get("request_mpesa_payment").actionEffect()).isEqualTo(ToolActionEffect.FINANCIAL);
        assertThat(tools.get("transfer_to_human").actionEffect()).isEqualTo(ToolActionEffect.TRANSFER);
        assertThat(tools.get("end_call").actionEffect()).isEqualTo(ToolActionEffect.TERMINAL);
        assertThat(tools.get("request_mpesa_payment").confirmationPolicy()).isEqualTo(ToolConfirmationPolicy.EXPLICIT);
        assertThat(tools.get("book_slot").confirmationPolicy()).isEqualTo(ToolConfirmationPolicy.VERIFIED_REVIEW);
    }

    @Test
    void repairsEveryCalendarActionFromAnyConnectedGoogleTool() {
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        var credentialId = UUID.randomUUID();
        when(agent.getId()).thenReturn(agentId);
        when(agent.isBookingEnabled()).thenReturn(true);
        var availability = tool(agent, "check_availability");
        var booking = tool(agent, "book_slot");
        var cancellation = tool(agent, "cancel_booking");
        availability.connectCalendar("google", credentialId);
        var repository = mock(AgentToolRepository.class);
        when(repository.existsByAgent_IdAndToolName(eq(agentId), anyString())).thenReturn(true);
        when(repository.findByAgent_IdOrderByDisplayOrderAsc(agentId))
                .thenReturn(List.of(availability, booking, cancellation));

        new DefaultToolSeeder(repository).seedDefaults(agent);

        assertThat(booking.getCalendarType()).isEqualTo("google");
        assertThat(booking.getCalendarCredentialId()).isEqualTo(credentialId);
        assertThat(cancellation.getCalendarCredentialId()).isEqualTo(credentialId);
        assertThat(booking.isActive()).isTrue();
    }

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
