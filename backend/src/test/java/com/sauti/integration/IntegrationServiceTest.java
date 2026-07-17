package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentTool;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.CalendarCredential;
import com.sauti.tool.CalendarCredentialRepository;
import com.sauti.tool.CredentialEncryption;
import com.sauti.tool.WebhookDestinationValidator;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationServiceTest {
    @Test
    void oauthReconnectPreservesRefreshTokenAndSheetsConfiguration() throws Exception {
        var objectMapper = new ObjectMapper();
        var encryption = new CredentialEncryption("dev-tool-encryption-key-32-bytes");
        var connections = mock(IntegrationConnectionRepository.class);
        var bindings = mock(AgentIntegrationRepository.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var oldCredentials = objectMapper.writeValueAsString(Map.of(
                "accessToken", "old-access",
                "refreshToken", "keep-refresh",
                "grantedAt", 1,
                "expiresIn", 3600));
        var connection = new IntegrationConnection(
                tenantId, "google_sheets", "Google Sheets",
                encryption.encrypt(oldCredentials),
                objectMapper.writeValueAsString(Map.of("spreadsheetId", "sheet-1", "range", "Calls!A:E")));

        when(connections.findFirstByTenantIdAndProviderOrderByCreatedAtDesc(tenantId, "google_sheets"))
                .thenReturn(Optional.of(connection));
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindings.findByTenantIdAndAgentIdAndProvider(tenantId, agentId, "google_sheets"))
                .thenReturn(Optional.empty());
        when(bindings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var service = new IntegrationService(
                objectMapper, encryption, new IntegrationCatalog(), connections, bindings,
                mock(IntegrationDeliveryRepository.class), mock(AgentRepository.class),
                mock(AgentToolRepository.class), mock(CalendarCredentialRepository.class),
                mock(WebhookDestinationValidator.class));

        service.connectOAuth(tenantId, agentId, "google_sheets", Map.of(
                "accessToken", "new-access",
                "refreshToken", "",
                "grantedAt", 200,
                "expiresIn", 3600), Map.of());

        assertThat(service.credentials(connection))
                .containsEntry("accessToken", "new-access")
                .containsEntry("refreshToken", "keep-refresh")
                .containsEntry("grantedAt", 200);
        assertThat(objectMapper.readTree(connection.getConfiguration()).path("spreadsheetId").asText())
                .isEqualTo("sheet-1");
    }

    @Test
    void enablingGoogleCalendarLinksTheWorkspaceCredentialToAgentTools() {
        var objectMapper = new ObjectMapper();
        var encryption = new CredentialEncryption("dev-tool-encryption-key-32-bytes");
        var connections = mock(IntegrationConnectionRepository.class);
        var bindings = mock(AgentIntegrationRepository.class);
        var deliveries = mock(IntegrationDeliveryRepository.class);
        var agents = mock(AgentRepository.class);
        var tools = mock(AgentToolRepository.class);
        var credentials = mock(CalendarCredentialRepository.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var agent = mock(Agent.class);
        var tool = mock(AgentTool.class);
        var credential = mock(CalendarCredential.class);
        var credentialId = UUID.randomUUID();
        var connection = new IntegrationConnection(
                tenantId, "google_calendar", "Google Calendar",
                encryption.encrypt("{\"accessToken\":\"token\"}"), "{\"calendarId\":\"primary\"}"
        );

        when(agents.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agent));
        when(connections.findByIdAndTenantId(connection.getId(), tenantId)).thenReturn(Optional.of(connection));
        when(connections.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(bindings.findByTenantIdAndAgentIdAndProvider(tenantId, agentId, "google_calendar"))
                .thenReturn(Optional.empty());
        when(bindings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveries.findFirstByAgentIntegrationIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(tool.getToolName()).thenReturn("book_slot");
        when(tools.findByAgent_IdOrderByDisplayOrderAsc(agentId)).thenReturn(List.of(tool));
        when(credentials.findAllByTenant_IdAndProviderOrderByCreatedAtDesc(tenantId, "google"))
                .thenReturn(List.of(credential));
        when(credential.getId()).thenReturn(credentialId);

        var service = new IntegrationService(
                objectMapper, encryption, new IntegrationCatalog(), connections, bindings, deliveries,
                agents, tools, credentials, mock(WebhookDestinationValidator.class)
        );

        var result = service.configure(tenantId, agentId, new IntegrationService.BindingRequest(
                "google_calendar", true, connection.getId(), Map.of()
        ));

        assertThat(result.enabled()).isTrue();
        assertThat(result.connectionStatus()).isEqualTo("connected");
        verify(tool).connectCalendar("google", credentialId);
        verify(agent).updateCalendarProvider("Google Calendar");
        verify(agents).save(agent);
    }

    @Test
    void startupReconciliationRepairsAnEnabledGoogleCalendarBinding() {
        var objectMapper = new ObjectMapper();
        var encryption = new CredentialEncryption("dev-tool-encryption-key-32-bytes");
        var connections = mock(IntegrationConnectionRepository.class);
        var bindings = mock(AgentIntegrationRepository.class);
        var agents = mock(AgentRepository.class);
        var tools = mock(AgentToolRepository.class);
        var credentials = mock(CalendarCredentialRepository.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var agent = mock(Agent.class);
        var tool = mock(AgentTool.class);
        var credential = mock(CalendarCredential.class);
        var credentialId = UUID.randomUUID();
        var connection = new IntegrationConnection(
                tenantId, "google_calendar", "Google Calendar",
                encryption.encrypt("{\"accessToken\":\"token\"}"), "{\"calendarId\":\"primary\"}"
        );
        var binding = new AgentIntegration(tenantId, agentId, "google_calendar");
        binding.configure(true, connection.getId(), "{}");

        when(bindings.findAllByProviderAndEnabledTrue("google_calendar")).thenReturn(List.of(binding));
        when(connections.findByIdAndTenantId(connection.getId(), tenantId)).thenReturn(Optional.of(connection));
        when(agents.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agent));
        when(tool.getToolName()).thenReturn("check_availability");
        when(tools.findByAgent_IdOrderByDisplayOrderAsc(agentId)).thenReturn(List.of(tool));
        when(credentials.findAllByTenant_IdAndProviderOrderByCreatedAtDesc(tenantId, "google"))
                .thenReturn(List.of(credential));
        when(credential.getId()).thenReturn(credentialId);

        var service = new IntegrationService(
                objectMapper, encryption, new IntegrationCatalog(), connections, bindings,
                mock(IntegrationDeliveryRepository.class), agents, tools, credentials,
                mock(WebhookDestinationValidator.class)
        );

        service.reconcileGoogleCalendarBindings();

        assertThat(binding.isEnabled()).isTrue();
        verify(tool).connectCalendar("google", credentialId);
        verify(agent).updateCalendarProvider("Google Calendar");
    }
}
