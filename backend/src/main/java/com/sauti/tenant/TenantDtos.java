package com.sauti.tenant;

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

}
