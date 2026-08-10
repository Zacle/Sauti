package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_add_on_subscriptions")
public class BillingAddOnSubscription extends Auditable {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false, unique = true, length = 100) private String providerSubscriptionId;
    @Column(nullable = false, length = 100) private String providerPlanId;
    @Column(nullable = false, length = 30) private String addOn;
    @Column(nullable = false, length = 30) private String providerStatus;
    @Column(nullable = false) private boolean testMode;
    private OffsetDateTime renewsAt;
    private OffsetDateTime endsAt;
    private OffsetDateTime providerUpdatedAt;
    @Column(length = 1000) private String manageUrl;

    protected BillingAddOnSubscription() { }

    public BillingAddOnSubscription(UUID tenantId, String provider, String providerSubscriptionId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.provider = required(provider);
        this.providerSubscriptionId = required(providerSubscriptionId);
    }

    public boolean isNewerThan(OffsetDateTime candidate) {
        if (providerUpdatedAt == null) return true;
        return candidate != null && !candidate.isBefore(providerUpdatedAt);
    }

    public void synchronize(String providerPlanId, String addOn, String providerStatus,
                            boolean testMode, OffsetDateTime renewsAt, OffsetDateTime endsAt,
                            OffsetDateTime providerUpdatedAt, String manageUrl) {
        this.providerPlanId = required(providerPlanId);
        this.addOn = required(addOn);
        this.providerStatus = required(providerStatus);
        this.testMode = testMode;
        this.renewsAt = renewsAt;
        this.endsAt = endsAt;
        this.providerUpdatedAt = providerUpdatedAt;
        this.manageUrl = optional(manageUrl);
    }

    public UUID getTenantId() { return tenantId; }
    public String getProvider() { return provider; }
    public String getProviderSubscriptionId() { return providerSubscriptionId; }
    public String getAddOn() { return addOn; }
    public String getProviderStatus() { return providerStatus; }
    public OffsetDateTime getRenewsAt() { return renewsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public String getManageUrl() { return manageUrl; }

    public boolean activeAt(OffsetDateTime now) {
        if (java.util.List.of("active", "trialing", "canceling").contains(providerStatus)) return true;
        return "canceled".equals(providerStatus) && endsAt != null && endsAt.isAfter(now);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Add-on subscription value is required");
        return value.trim();
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
