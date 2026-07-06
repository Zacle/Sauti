package com.sauti.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "integration_deliveries")
public class IntegrationDelivery {
    @Id private UUID id;
    private UUID tenantId;
    private UUID jobId;
    private UUID callId;
    private UUID agentIntegrationId;
    private String provider;
    private String status;
    private String idempotencyKey;
    private int attempts;
    private OffsetDateTime nextAttemptAt;
    private Integer responseCode;
    private String lastError;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected IntegrationDelivery() {}

    public IntegrationDelivery(PostCallJob job, AgentIntegration binding) {
        this.id = UUID.randomUUID();
        this.tenantId = job.getTenantId();
        this.jobId = job.getId();
        this.callId = job.getCallId();
        this.agentIntegrationId = binding.getId();
        this.provider = binding.getProvider();
        this.status = "pending";
        this.idempotencyKey = job.getCallId() + ":" + binding.getId();
        this.nextAttemptAt = OffsetDateTime.now();
        this.createdAt = nextAttemptAt;
        this.updatedAt = nextAttemptAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getJobId() { return jobId; }
    public UUID getCallId() { return callId; }
    public UUID getAgentIntegrationId() { return agentIntegrationId; }
    public String getProvider() { return provider; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Integer getResponseCode() { return responseCode; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void delivered(int responseCode) {
        attempts++;
        this.responseCode = responseCode;
        status = "delivered";
        deliveredAt = OffsetDateTime.now();
        lastError = null;
        updatedAt = deliveredAt;
    }

    public void retry(Integer responseCode, String error) {
        attempts++;
        this.responseCode = responseCode;
        lastError = error;
        status = attempts >= 5 ? "failed" : "retrying";
        nextAttemptAt = OffsetDateTime.now().plusSeconds((long) Math.pow(2, attempts) * 30);
        updatedAt = OffsetDateTime.now();
    }
}
