package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.CredentialEncryption;
import com.sauti.tool.WebhookDestinationValidator;
import java.util.Map;
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
                mock(AgentToolRepository.class), mock(WebhookDestinationValidator.class));

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
}
