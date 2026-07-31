package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.tool.AgentTool;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.CalendarCredential;
import com.sauti.tool.CalendarCredentialRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingConflictScopeServiceTest {
    @Test
    void keepsAnAgentIndependentWithoutASharedGoogleCalendar() {
        var agentId = UUID.randomUUID();
        var tools = mock(AgentToolRepository.class);
        var credentials = mock(CalendarCredentialRepository.class);
        var service = new BookingConflictScopeService(tools, credentials, mock(AgentRepository.class));

        var scope = service.resolve(UUID.randomUUID(), agentId);

        assertThat(scope.calendarCredentialId()).isNull();
        assertThat(scope.agentIds()).containsExactly(agentId);
    }

    @Test
    void includesEveryAgentUsingTheSameActiveGoogleCalendarCredential() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var otherAgentId = UUID.randomUUID();
        var credentialId = UUID.randomUUID();
        var tool = mock(AgentTool.class);
        when(tool.getCalendarType()).thenReturn("google");
        when(tool.getCalendarCredentialId()).thenReturn(credentialId);
        var tools = mock(AgentToolRepository.class);
        when(tools.findByAgent_IdAndToolNameAndIsActiveTrue(agentId, "check_availability"))
                .thenReturn(Optional.of(tool));
        when(tools.findActiveAgentIdsSharingCalendar(tenantId, credentialId, "check_availability"))
                .thenReturn(List.of(otherAgentId));
        var credentials = mock(CalendarCredentialRepository.class);
        when(credentials.findByIdAndTenant_Id(credentialId, tenantId))
                .thenReturn(Optional.of(mock(CalendarCredential.class)));
        var service = new BookingConflictScopeService(tools, credentials, mock(AgentRepository.class));

        var scope = service.resolve(tenantId, agentId);

        assertThat(scope.calendarCredentialId()).isEqualTo(credentialId);
        assertThat(scope.agentIds()).containsExactly(otherAgentId, agentId);
    }

    @Test
    void locksTheSharedCredentialBeforeTheFinalConflictCheck() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var credentialId = UUID.randomUUID();
        var tool = mock(AgentTool.class);
        when(tool.getCalendarType()).thenReturn("google");
        when(tool.getCalendarCredentialId()).thenReturn(credentialId);
        var tools = mock(AgentToolRepository.class);
        when(tools.findByAgent_IdAndToolNameAndIsActiveTrue(agentId, "check_availability"))
                .thenReturn(Optional.of(tool));
        when(tools.findActiveAgentIdsSharingCalendar(tenantId, credentialId, "check_availability"))
                .thenReturn(List.of(agentId));
        var credentials = mock(CalendarCredentialRepository.class);
        when(credentials.findByIdAndTenantIdForUpdate(credentialId, tenantId))
                .thenReturn(Optional.of(mock(CalendarCredential.class)));
        var service = new BookingConflictScopeService(tools, credentials, mock(AgentRepository.class));

        var scope = service.resolveAndLock(tenantId, agentId);

        assertThat(scope.agentIds()).containsExactly(agentId);
        verify(credentials).findByIdAndTenantIdForUpdate(credentialId, tenantId);
    }

    @Test
    void locksTheAgentForALocalOnlyFinalConflictCheck() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var tools = mock(AgentToolRepository.class);
        var credentials = mock(CalendarCredentialRepository.class);
        var agents = mock(AgentRepository.class);
        when(agents.findByIdAndTenantIdForUpdate(agentId, tenantId))
                .thenReturn(Optional.of(mock(Agent.class)));
        var service = new BookingConflictScopeService(tools, credentials, agents);

        var scope = service.resolveAndLock(tenantId, agentId);

        assertThat(scope.agentIds()).containsExactly(agentId);
        verify(agents).findByIdAndTenantIdForUpdate(agentId, tenantId);
    }
}
