package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.tenant.TenantDtos.OnboardingStatusResponse;
import com.sauti.tenant.TenantDtos.TenantWebhookRequest;
import com.sauti.tenant.TenantDtos.TenantWebhookResponse;
import com.sauti.tenant.TenantDtos.PrivacyRetentionRequest;
import com.sauti.tenant.TenantDtos.PrivacyRetentionResponse;
import com.sauti.tenant.TenantDtos.WorkspaceProfileRequest;
import com.sauti.tenant.TenantDtos.WorkspaceProfileResponse;
import com.sauti.tenant.TenantDtos.WorkspaceCallDefaultsRequest;
import com.sauti.tenant.TenantDtos.WorkspaceCallDefaultsResponse;
import com.sauti.tenant.TenantDtos.WorkspaceNotificationPreferencesRequest;
import com.sauti.tenant.TenantDtos.WorkspaceNotificationPreferencesResponse;
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

    @GetMapping("/privacy-retention")
    PrivacyRetentionResponse privacyRetention(@AuthenticationPrincipal AuthenticatedUser user) {
        return tenantSettingsService.privacyRetention(user.tenantId());
    }

    @GetMapping("/workspace-profile")
    WorkspaceProfileResponse workspaceProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return tenantSettingsService.workspaceProfile(user.tenantId());
    }

    @PutMapping("/workspace-profile")
    WorkspaceProfileResponse configureWorkspaceProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody WorkspaceProfileRequest request
    ) {
        return tenantSettingsService.configureWorkspaceProfile(user.tenantId(), request);
    }

    @GetMapping("/call-defaults")
    WorkspaceCallDefaultsResponse callDefaults(@AuthenticationPrincipal AuthenticatedUser user) {
        return tenantSettingsService.callDefaults(user.tenantId());
    }

    @PutMapping("/call-defaults")
    WorkspaceCallDefaultsResponse configureCallDefaults(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody WorkspaceCallDefaultsRequest request
    ) {
        return tenantSettingsService.configureCallDefaults(user.tenantId(), request);
    }

    @GetMapping("/notification-preferences")
    WorkspaceNotificationPreferencesResponse notificationPreferences(@AuthenticationPrincipal AuthenticatedUser user) {
        return tenantSettingsService.notificationPreferences(user.tenantId());
    }

    @PutMapping("/notification-preferences")
    WorkspaceNotificationPreferencesResponse configureNotificationPreferences(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody WorkspaceNotificationPreferencesRequest request
    ) {
        return tenantSettingsService.configureNotificationPreferences(user.tenantId(), request);
    }

    @PutMapping("/privacy-retention")
    PrivacyRetentionResponse configurePrivacyRetention(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PrivacyRetentionRequest request
    ) {
        return tenantSettingsService.configurePrivacyRetention(user.tenantId(), request);
    }
}
