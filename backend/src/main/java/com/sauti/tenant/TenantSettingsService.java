package com.sauti.tenant;

import com.sauti.tenant.TenantDtos.TenantWebhookRequest;
import com.sauti.tool.WebhookDestinationValidator;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSettingsService {
    private final TenantRepository tenantRepository;
    private final WebhookDestinationValidator webhookDestinationValidator;

    public TenantSettingsService(TenantRepository tenantRepository, WebhookDestinationValidator webhookDestinationValidator) {
        this.tenantRepository = tenantRepository;
        this.webhookDestinationValidator = webhookDestinationValidator;
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
}
