package com.sauti.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.tenant.TenantDtos.PrivacyRetentionRequest;
import com.sauti.tool.WebhookDestinationValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantSettingsPrivacyTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final AgentRepository agents = mock(AgentRepository.class);
    private final TenantSettingsService service = new TenantSettingsService(
            tenants, mock(WebhookDestinationValidator.class), agents
    );

    @Test
    void savesBoundedRetentionWhenNoRecordingAgentExists() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(agents.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId())).thenReturn(List.of());

        var result = service.configurePrivacyRetention(tenant.getId(),
                new PrivacyRetentionRequest(180, 30, false));

        assertThat(result.conversationRetentionDays()).isEqualTo(180);
        assertThat(result.recordingRetentionDays()).isEqualTo(30);
    }

    @Test
    void requiresComplianceAcknowledgementWhileRecordingIsEnabled() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var agent = mock(Agent.class);
        when(agent.isRecordCalls()).thenReturn(true);
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(agents.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId())).thenReturn(List.of(agent));

        assertThatThrownBy(() -> service.configurePrivacyRetention(tenant.getId(),
                new PrivacyRetentionRequest(90, 30, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recording consent");
    }

    @Test
    void refusesRecordingRetentionLongerThanConversationRetention() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(agents.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.configurePrivacyRetention(tenant.getId(),
                new PrivacyRetentionRequest(30, 90, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed");
    }
}
