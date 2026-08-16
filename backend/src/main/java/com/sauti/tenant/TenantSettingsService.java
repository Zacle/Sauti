package com.sauti.tenant;

import com.sauti.tenant.TenantDtos.TenantWebhookRequest;
import com.sauti.tenant.TenantDtos.PrivacyRetentionRequest;
import com.sauti.tenant.TenantDtos.PrivacyRetentionResponse;
import com.sauti.tenant.TenantDtos.WorkspaceProfileRequest;
import com.sauti.tenant.TenantDtos.WorkspaceProfileResponse;
import com.sauti.agent.AgentRepository;
import com.sauti.tool.WebhookDestinationValidator;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSettingsService {
    private final TenantRepository tenantRepository;
    private final WebhookDestinationValidator webhookDestinationValidator;
    private final AgentRepository agents;

    public TenantSettingsService(TenantRepository tenantRepository,
                                 WebhookDestinationValidator webhookDestinationValidator,
                                 AgentRepository agents) {
        this.tenantRepository = tenantRepository;
        this.webhookDestinationValidator = webhookDestinationValidator;
        this.agents = agents;
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    }

    @Transactional
    public Tenant configureWebhook(UUID tenantId, TenantWebhookRequest request) {
        var tenant = get(tenantId);
        var url = request.webhookUrl() == null || request.webhookUrl().isBlank() ? null : request.webhookUrl().trim();
        if (url != null) {
            webhookDestinationValidator.validateHttpsPublicUrl(url);
        }
        var secret = request.webhookSecret() == null || request.webhookSecret().isBlank()
                ? tenant.getWebhookSecret()
                : request.webhookSecret().trim();
        tenant.configureWebhook(url, secret);
        return tenant;
    }

    @Transactional(readOnly = true)
    public PrivacyRetentionResponse privacyRetention(UUID tenantId) {
        var tenant = get(tenantId);
        return privacyResponse(tenant);
    }

    @Transactional(readOnly = true)
    public WorkspaceProfileResponse workspaceProfile(UUID tenantId) {
        return WorkspaceProfileResponse.from(get(tenantId));
    }

    @Transactional
    public WorkspaceProfileResponse configureWorkspaceProfile(UUID tenantId, WorkspaceProfileRequest request) {
        var tenant = get(tenantId);
        tenant.configureWorkspaceProfile(
                request.businessName(),
                request.timezone(),
                request.defaultBookingDurationMinutes()
        );
        return WorkspaceProfileResponse.from(tenant);
    }

    @Transactional
    public PrivacyRetentionResponse configurePrivacyRetention(UUID tenantId, PrivacyRetentionRequest request) {
        var tenant = get(tenantId);
        var recordingEnabled = agents.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .anyMatch(com.sauti.agent.Agent::isRecordCalls);
        if (recordingEnabled && !request.recordingComplianceAcknowledged()) {
            throw new IllegalArgumentException(
                    "Confirm that your caller notice and recording consent process meet applicable requirements"
            );
        }
        tenant.configurePrivacyRetention(request.conversationRetentionDays(), request.recordingRetentionDays());
        return privacyResponse(tenant);
    }

    private PrivacyRetentionResponse privacyResponse(Tenant tenant) {
        return new PrivacyRetentionResponse(
                tenant.getConversationRetentionDays(),
                tenant.getRecordingRetentionDays(),
                agents.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
                        .anyMatch(com.sauti.agent.Agent::isRecordCalls),
                "2026-08-12"
        );
    }
}
