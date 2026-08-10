package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_plan_change_requests")
public class BillingPlanChangeRequest extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID tenantId;
    @Column(nullable = false, length = 100) private String providerSubscriptionId;
    @Column(nullable = false, length = 20) private String currentPlan;
    @Column(nullable = false, length = 20) private String targetPlan;
    @Column(nullable = false, length = 20) private String targetInterval;
    @Column(nullable = false, length = 20) private String status;
    private OffsetDateTime effectiveAt;

    protected BillingPlanChangeRequest() { }

    public BillingPlanChangeRequest(UUID tenantId, String providerSubscriptionId, String currentPlan,
                                    String targetPlan, String targetInterval, OffsetDateTime effectiveAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        retarget(providerSubscriptionId, currentPlan, targetPlan, targetInterval, effectiveAt);
    }

    public void retarget(String providerSubscriptionId, String currentPlan, String targetPlan,
                         String targetInterval, OffsetDateTime effectiveAt) {
        this.providerSubscriptionId = required(providerSubscriptionId);
        this.currentPlan = required(currentPlan);
        this.targetPlan = required(targetPlan);
        this.targetInterval = required(targetInterval);
        this.effectiveAt = effectiveAt;
        this.status = "requested";
    }

    public void complete() { this.status = "completed"; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProviderSubscriptionId() { return providerSubscriptionId; }
    public String getCurrentPlan() { return currentPlan; }
    public String getTargetPlan() { return targetPlan; }
    public String getTargetInterval() { return targetInterval; }
    public String getStatus() { return status; }
    public OffsetDateTime getEffectiveAt() { return effectiveAt; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Plan change value is required");
        return value.trim();
    }
}
