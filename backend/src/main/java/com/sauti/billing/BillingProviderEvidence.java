package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable, non-sensitive evidence derived from a verified provider event.
 * The signed raw payload remains in the private provider inbox and is never
 * copied into API-facing evidence.
 */
@Entity
@Table(name = "billing_provider_evidence")
public class BillingProviderEvidence extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID sourceEventId;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false, length = 30) private String recordType;
    @Column(nullable = false, length = 50) private String eventName;
    @Column(nullable = false, length = 100) private String providerResourceId;
    @Column(length = 100) private String providerPaymentId;
    @Column(length = 100) private String providerMembershipId;
    @Column(length = 100) private String providerPlanId;
    @Column(nullable = false, length = 40) private String normalizedStatus;
    @Column(precision = 19, scale = 4) private BigDecimal amount;
    @Column(length = 3) private String currency;
    @Column(nullable = false) private boolean testMode;
    @Column(nullable = false) private OffsetDateTime occurredAt;

    protected BillingProviderEvidence() { }

    public BillingProviderEvidence(UUID sourceEventId, UUID tenantId, String provider,
                                   String recordType, String eventName, String providerResourceId,
                                   String providerPaymentId, String providerMembershipId,
                                   String providerPlanId, String normalizedStatus,
                                   BigDecimal amount, String currency, boolean testMode,
                                   OffsetDateTime occurredAt) {
        this.id = UUID.randomUUID();
        this.sourceEventId = sourceEventId;
        this.tenantId = tenantId;
        this.provider = required(provider);
        this.recordType = required(recordType);
        this.eventName = required(eventName);
        this.providerResourceId = required(providerResourceId);
        this.providerPaymentId = optional(providerPaymentId);
        this.providerMembershipId = optional(providerMembershipId);
        this.providerPlanId = optional(providerPlanId);
        this.normalizedStatus = required(normalizedStatus);
        this.amount = amount;
        this.currency = optional(currency) == null ? null : currency.trim().toUpperCase();
        this.testMode = testMode;
        this.occurredAt = occurredAt == null ? OffsetDateTime.now() : occurredAt;
    }

    public UUID getSourceEventId() { return sourceEventId; }
    public UUID getTenantId() { return tenantId; }
    public String getProvider() { return provider; }
    public String getRecordType() { return recordType; }
    public String getEventName() { return eventName; }
    public String getProviderResourceId() { return providerResourceId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getProviderMembershipId() { return providerMembershipId; }
    public String getProviderPlanId() { return providerPlanId; }
    public String getNormalizedStatus() { return normalizedStatus; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public boolean isTestMode() { return testMode; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Evidence value is required");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
