package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_subscriptions")
public class BillingSubscription extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID tenantId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false, unique = true, length = 100) private String providerSubscriptionId;
    @Column(nullable = false, length = 100) private String providerCustomerId;
    @Column(nullable = false, length = 100) private String providerOrderId;
    @Column(nullable = false, length = 100) private String providerProductId;
    @Column(nullable = false, length = 100) private String providerVariantId;
    @Column(nullable = false, length = 20) private String plan;
    @Column(nullable = false, length = 20) private String billingInterval;
    @Column(nullable = false, length = 30) private String providerStatus;
    @Column(nullable = false) private boolean testMode;
    private OffsetDateTime renewsAt;
    private OffsetDateTime endsAt;
    private OffsetDateTime trialEndsAt;
    private OffsetDateTime providerUpdatedAt;
    @Column(length = 30) private String cardBrand;
    @Column(length = 4) private String cardLastFour;
    @Column(length = 1000) private String updatePaymentMethodUrl;

    protected BillingSubscription() { }

    public BillingSubscription(UUID tenantId, String provider, String providerSubscriptionId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.provider = required(provider);
        this.providerSubscriptionId = providerSubscriptionId;
    }

    public boolean isNewerThan(OffsetDateTime candidate) {
        if (providerUpdatedAt == null) return true;
        return candidate != null && !candidate.isBefore(providerUpdatedAt);
    }

    public void synchronize(String customerId, String orderId, String productId, String variantId,
                            String plan, String interval, String status, boolean testMode,
                            OffsetDateTime renewsAt, OffsetDateTime endsAt, OffsetDateTime trialEndsAt,
                            OffsetDateTime providerUpdatedAt, String cardBrand, String cardLastFour,
                            String updatePaymentMethodUrl) {
        this.providerCustomerId = required(customerId);
        this.providerOrderId = required(orderId);
        this.providerProductId = required(productId);
        this.providerVariantId = required(variantId);
        this.plan = required(plan);
        this.billingInterval = required(interval);
        this.providerStatus = required(status);
        this.testMode = testMode;
        this.renewsAt = renewsAt;
        this.endsAt = endsAt;
        this.trialEndsAt = trialEndsAt;
        this.providerUpdatedAt = providerUpdatedAt;
        this.cardBrand = optional(cardBrand);
        this.cardLastFour = optional(cardLastFour);
        this.updatePaymentMethodUrl = optional(updatePaymentMethodUrl);
    }

    public void replaceProviderSubscription(String providerSubscriptionId) {
        this.providerSubscriptionId = required(providerSubscriptionId);
        this.providerUpdatedAt = null;
    }

    public UUID getTenantId() { return tenantId; }
    public String getProvider() { return provider; }
    public String getProviderSubscriptionId() { return providerSubscriptionId; }
    public String getProviderStatus() { return providerStatus; }
    public String getProviderCustomerId() { return providerCustomerId; }
    public OffsetDateTime getProviderUpdatedAt() { return providerUpdatedAt; }
    public String getPlan() { return plan; }
    public String getBillingInterval() { return billingInterval; }
    public OffsetDateTime getRenewsAt() { return renewsAt; }
    public String getUpdatePaymentMethodUrl() { return updatePaymentMethodUrl; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Subscription value is required");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
