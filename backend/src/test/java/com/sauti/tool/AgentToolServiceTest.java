package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentToolDtos.AgentToolRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentToolServiceTest {
    @Test
    void defaultsUnknownPostWebhooksToConfirmedWrites() {
        var fixture = fixture();

        var tool = fixture.service.create(fixture.tenantId, fixture.agentId, request(
                "synchronize_claim", "webhook", null, null, "POST"
        ));

        assertThat(tool.actionEffect()).isEqualTo(ToolActionEffect.DATA_WRITE);
        assertThat(tool.confirmationPolicy()).isEqualTo(ToolConfirmationPolicy.EXPLICIT);
    }

    @Test
    void allowsCustomReadOnlyToolsToDeclareTheirCapabilityWithoutNameConventions() {
        var fixture = fixture();

        var tool = fixture.service.create(fixture.tenantId, fixture.agentId, request(
                "retrieve_complex_case_context", "webhook", "read_only", "none", "GET"
        ));

        assertThat(tool.actionEffect()).isEqualTo(ToolActionEffect.READ_ONLY);
        assertThat(tool.confirmationPolicy()).isEqualTo(ToolConfirmationPolicy.NONE);
    }

    @Test
    void rejectsHighImpactToolsWithoutConfirmation() {
        var fixture = fixture();

        assertThatThrownBy(() -> fixture.service.create(fixture.tenantId, fixture.agentId, request(
                "initiate_financial_operation", "noop", "financial", "none", "POST"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("require confirmation");
    }

    private Fixture fixture() {
        var agentRepository = mock(AgentRepository.class);
        var toolRepository = mock(AgentToolRepository.class);
        var encryption = mock(CredentialEncryption.class);
        var validator = mock(WebhookDestinationValidator.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var agent = mock(Agent.class);
        when(agent.getId()).thenReturn(agentId);
        when(agentRepository.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agent));
        when(toolRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new Fixture(
                new AgentToolService(agentRepository, toolRepository, encryption, validator),
                tenantId,
                agentId
        );
    }

    private AgentToolRequest request(
            String name,
            String fulfillment,
            String effect,
            String confirmation,
            String method
    ) {
        return new AgentToolRequest(
                name,
                "Test tool",
                Map.of("type", "object", "properties", Map.of()),
                fulfillment,
                effect,
                confirmation,
                "webhook".equals(fulfillment) ? "https://example.com/tool" : null,
                method,
                "none",
                null,
                null,
                null,
                null,
                true,
                1
        );
    }

    private record Fixture(AgentToolService service, UUID tenantId, UUID agentId) { }
}
