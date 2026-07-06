package com.sauti.telnyx;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "telnyx_webhook_events")
public class TelnyxWebhookEvent extends Auditable {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String providerEventId;
    @Column(nullable = false)
    private String eventType;
    private String callControlId;
    @Column(nullable = false)
    private String status;
    private String failureReason;
    private OffsetDateTime occurredAt;

    protected TelnyxWebhookEvent() {
    }

    public TelnyxWebhookEvent(
            String providerEventId,
            String eventType,
            String callControlId,
            OffsetDateTime occurredAt
    ) {
        this.id = UUID.randomUUID();
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.callControlId = callControlId;
        this.occurredAt = occurredAt;
        this.status = "received";
    }

    public void markProcessing() { this.status = "processing"; }
    public void markCompleted() { this.status = "completed"; this.failureReason = null; }
    public void markFailed(String reason) {
        this.status = "failed";
        this.failureReason = reason == null ? "Unknown Telnyx webhook failure"
                : reason.substring(0, Math.min(1000, reason.length()));
    }
}
