package com.sauti.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.tenant.TenantDtos.PrivacyRetentionRequest;
import com.sauti.tenant.TenantDtos.WorkspaceProfileRequest;
import com.sauti.tenant.TenantDtos.WorkspaceCallDefaultsRequest;
import com.sauti.tenant.TenantDtos.WorkspaceNotificationPreferencesRequest;
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

    @Test
    void savesValidatedWorkspaceOwnedDefaults() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        var result = service.configureWorkspaceProfile(
                tenant.getId(),
                new WorkspaceProfileRequest("Clinic Group", "Europe/London", 45)
        );

        assertThat(result.businessName()).isEqualTo("Clinic Group");
        assertThat(result.timezone()).isEqualTo("Europe/London");
        assertThat(result.defaultBookingDurationMinutes()).isEqualTo(45);
    }

    @Test
    void rejectsInvalidWorkspaceTimezone() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.configureWorkspaceProfile(
                tenant.getId(),
                new WorkspaceProfileRequest("Clinic", "Not/A-Timezone", 60)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid IANA timezone");
    }

    @Test
    void savesWorkspaceCallAndNotificationPreferences() {
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        var calls = service.configureCallDefaults(
                tenant.getId(), new WorkspaceCallDefaultsRequest(false, true, 0.9));
        var notifications = service.configureNotificationPreferences(
                tenant.getId(), new WorkspaceNotificationPreferencesRequest(false, true));

        assertThat(calls.saveTranscript()).isFalse();
        assertThat(calls.recordCalls()).isTrue();
        assertThat(calls.bargeInSensitivity()).isEqualTo(0.9);
        assertThat(notifications.consoleBookingNotificationsEnabled()).isFalse();
        assertThat(notifications.emailBookingNotificationsEnabled()).isTrue();
    }
}
