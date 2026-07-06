package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.agent.AgentDtos.AgentResponse;
import com.sauti.agent.OnboardingCompletionService;
import com.sauti.agent.OnboardingDtos.CompleteOnboardingRequest;
import com.sauti.tenant.TenantDtos.OnboardingStatusResponse;
import com.sauti.tenant.TenantDtos.TenantWebhookRequest;
import com.sauti.tenant.TenantDtos.TenantWebhookResponse;
import com.sauti.tenant.TenantFlowService;
import com.sauti.tenant.TenantSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {
    private final TenantFlowService tenantFlowService;
    private final TenantSettingsService tenantSettingsService;
    private final OnboardingCompletionService onboardingCompletionService;

    public TenantController(
            TenantFlowService tenantFlowService,
            TenantSettingsService tenantSettingsService,
            OnboardingCompletionService onboardingCompletionService
    ) {
        this.tenantFlowService = tenantFlowService;
        this.tenantSettingsService = tenantSettingsService;
        this.onboardingCompletionService = onboardingCompletionService;
    }

    @GetMapping("/onboarding-status")
    OnboardingStatusResponse onboardingStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return tenantFlowService.onboardingStatus(user.tenantId(), user.userId());
    }

    @PostMapping("/onboarding")
    AgentResponse completeOnboarding(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CompleteOnboardingRequest request
    ) {
        return AgentResponse.from(onboardingCompletionService.complete(user.tenantId(), request));
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
