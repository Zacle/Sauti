package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_cost_reconciliation_jobs")
public class ProviderCostReconciliationJob extends Auditable {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false, length = 30) private String resourceType;
    @Column(nullable = false, length = 160) private String providerResourceId;
    @Column(nullable = false, length = 160) private String localReference;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private OffsetDateTime nextAttemptAt;
    @Column(length = 1000) private String lastError;
    private OffsetDateTime resourceOccurredAt;

    protected ProviderCostReconciliationJob() { }

    public ProviderCostReconciliationJob(UUID tenantId, String provider, String resourceType,
                                         String providerResourceId, String localReference,
                                         OffsetDateTime resourceOccurredAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.provider = required(provider);
        this.resourceType = required(resourceType);
        this.providerResourceId = required(providerResourceId);
        this.localReference = required(localReference);
        this.status = "pending";
        this.nextAttemptAt = OffsetDateTime.now();
        this.resourceOccurredAt = resourceOccurredAt;
    }

    public void reconciled() { status = "reconciled"; lastError = null; }
    public void retry(String error, OffsetDateTime nextAttemptAt) {
        attempts++;
        status = "retrying";
        lastError = trim(error);
        this.nextAttemptAt = nextAttemptAt;
    }
    public void estimated(String error) { attempts++; status = "estimated"; lastError = trim(error); }
    public void unavailable(String error) { attempts++; status = "unavailable"; lastError = trim(error); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProvider() { return provider; }
    public String getResourceType() { return resourceType; }
    public String getProviderResourceId() { return providerResourceId; }
    public String getLocalReference() { return localReference; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime getResourceOccurredAt() { return resourceOccurredAt; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Reconciliation value is required");
        return value.trim();
    }
    private static String trim(String value) {
        var normalized = value == null || value.isBlank() ? "Provider cost is not available" : value.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }
}
