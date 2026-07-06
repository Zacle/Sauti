package com.sauti.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "post_call_jobs")
public class PostCallJob {
    @Id private UUID id;
    private UUID tenantId;
    private UUID callId;
    private String status;
    private boolean test;
    private int attempts;
    private OffsetDateTime nextAttemptAt;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected PostCallJob() {}

    public PostCallJob(UUID tenantId, UUID callId, boolean test) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.callId = callId;
        this.status = "pending_analysis";
        this.test = test;
        this.nextAttemptAt = OffsetDateTime.now();
        this.createdAt = nextAttemptAt;
        this.updatedAt = nextAttemptAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCallId() { return callId; }
    public String getStatus() { return status; }
    public boolean isTest() { return test; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }

    public void ready() { status = "ready"; updatedAt = OffsetDateTime.now(); }
    public void completed() { status = "completed"; lastError = null; updatedAt = OffsetDateTime.now(); }
    public void failed(String error) {
        attempts++;
        lastError = error;
        status = attempts >= 5 ? "failed" : "ready";
        nextAttemptAt = OffsetDateTime.now().plusSeconds((long) Math.pow(2, attempts) * 30);
        updatedAt = OffsetDateTime.now();
    }
}
