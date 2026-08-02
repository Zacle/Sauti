package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_provider_events")
public class BillingProviderEvent extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 64) private String payloadHash;
    @Column(nullable = false, length = 50) private String eventName;
    @Column(nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private OffsetDateTime nextAttemptAt;
    @Column(length = 1000) private String lastError;

    protected BillingProviderEvent() { }

    public BillingProviderEvent(String payloadHash, String eventName, String payloadJson) {
        this.id = UUID.randomUUID();
        this.payloadHash = payloadHash;
        this.eventName = eventName;
        this.payloadJson = payloadJson;
        this.status = "pending";
        this.nextAttemptAt = OffsetDateTime.now();
    }

    public void processed() { status = "processed"; lastError = null; }
    public void retry(String error) {
        attempts++;
        status = attempts >= 8 ? "failed" : "retrying";
        nextAttemptAt = OffsetDateTime.now().plusSeconds(Math.min(3600, 5L << Math.min(attempts, 9)));
        var message = error == null || error.isBlank() ? "Billing event processing failed" : error.trim();
        lastError = message.substring(0, Math.min(1000, message.length()));
    }

    public UUID getId() { return id; }
    public String getEventName() { return eventName; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
}
