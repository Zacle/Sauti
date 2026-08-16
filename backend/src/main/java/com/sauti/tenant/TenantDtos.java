package com.sauti.tenant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class TenantDtos {
    private TenantDtos() {
    }

    public record TenantResponse(
            UUID id,
            String businessName,
            String email,
            String countryCode,
            String plan,
            String status,
            int monthlyMinutesLimit,
            int minutesUsedThisCycle
    ) {
        public static TenantResponse from(Tenant tenant) {
            return new TenantResponse(
                    tenant.getId(),
                    tenant.getBusinessName(),
                    tenant.getEmail(),
                    tenant.getCountryCode(),
                    tenant.getPlan(),
                    tenant.getStatus(),
                    tenant.getMonthlyMinutesLimit(),
                    tenant.getMinutesUsedThisCycle()
            );
        }
    }

    public record OnboardingStatusResponse(
            boolean registered,
            boolean emailVerified,
            boolean hasAgent,
            boolean hasActiveAgent,
            boolean hasProvisionedNumber,
            String nextStep
    ) {
    }

    public record TenantWebhookRequest(
            String webhookUrl,
            String webhookSecret
    ) {
    }

    public record TenantWebhookResponse(
            String webhookUrl,
            boolean secretConfigured
    ) {
        public static TenantWebhookResponse from(Tenant tenant) {
            return new TenantWebhookResponse(
                    tenant.getWebhookUrl(),
                    tenant.getWebhookSecret() != null && !tenant.getWebhookSecret().isBlank()
            );
        }
    }

    public record PrivacyRetentionRequest(
            int conversationRetentionDays,
            int recordingRetentionDays,
            boolean recordingComplianceAcknowledged
    ) {
    }

    public record PrivacyRetentionResponse(
            int conversationRetentionDays,
            int recordingRetentionDays,
            boolean recordingEnabledForAnyAgent,
            String policyVersion
    ) {
    }

    public record WorkspaceProfileRequest(
            @NotBlank @Size(min = 2, max = 120) String businessName,
            @NotBlank @Size(max = 100) String timezone,
            @Min(5) @Max(480) int defaultBookingDurationMinutes
    ) {
    }

    public record WorkspaceProfileResponse(
            String businessName,
            String ownerEmail,
            String countryCode,
            String timezone,
            int defaultBookingDurationMinutes
    ) {
        public static WorkspaceProfileResponse from(Tenant tenant) {
            return new WorkspaceProfileResponse(
                    tenant.getBusinessName(),
                    tenant.getEmail(),
                    tenant.getCountryCode(),
                    tenant.getTimezone(),
                    tenant.getDefaultBookingDurationMinutes()
            );
        }
    }

    public record WorkspaceCallDefaultsRequest(
            boolean saveTranscript,
            boolean recordCalls,
            @jakarta.validation.constraints.DecimalMin("0.0")
            @jakarta.validation.constraints.DecimalMax("1.0") double bargeInSensitivity
    ) { }

    public record WorkspaceCallDefaultsResponse(
            boolean saveTranscript,
            boolean recordCalls,
            double bargeInSensitivity
    ) {
        public static WorkspaceCallDefaultsResponse from(Tenant tenant) {
            return new WorkspaceCallDefaultsResponse(
                    tenant.isDefaultSaveTranscript(),
                    tenant.isDefaultRecordCalls(),
                    tenant.getDefaultBargeInSensitivity()
            );
        }
    }

    public record WorkspaceNotificationPreferencesRequest(
            boolean consoleBookingNotificationsEnabled,
            boolean emailBookingNotificationsEnabled
    ) { }

    public record WorkspaceNotificationPreferencesResponse(
            boolean consoleBookingNotificationsEnabled,
            boolean emailBookingNotificationsEnabled
    ) {
        public static WorkspaceNotificationPreferencesResponse from(Tenant tenant) {
            return new WorkspaceNotificationPreferencesResponse(
                    tenant.isConsoleBookingNotificationsEnabled(),
                    tenant.isEmailBookingNotificationsEnabled()
            );
        }
    }

}
