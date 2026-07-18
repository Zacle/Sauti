package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.tenant.TenantDtos.OnboardingStatusResponse;
import com.sauti.tenant.TenantDtos.TenantWebhookRequest;
import com.sauti.tenant.TenantDtos.TenantWebhookResponse;
import com.sauti.tenant.TenantFlowService;
import com.sauti.tenant.TenantSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {
    private final TenantFlowService tenantFlowService;
    private final TenantSettingsService tenantSettingsService;

    public TenantController(
            TenantFlowService tenantFlowService,
            TenantSettingsService tenantSettingsService
    ) {
        this.tenantFlowService = tenantFlowService;
        this.tenantSettingsService = tenantSettingsService;
    }

    @GetMapping("/onboarding-status")
    OnboardingStatusResponse onboardingStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return tenantFlowService.onboardingStatus(user.tenantId(), user.userId());
    }

    @GetMapping("/webhook")
    TenantWebhookResponse webhook(@AuthenticationPrincipal AuthenticatedUser user) {
        return TenantWebhookResponse.from(tenantSettingsService.get(user.tenantId()));
    }

    @PutMapping("/webhook")
    TenantWebhookResponse configureWebhook(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TenantWebhookRequest request
    ) {
        return TenantWebhookResponse.from(tenantSettingsService.configureWebhook(user.tenantId(), request));
    }
}
