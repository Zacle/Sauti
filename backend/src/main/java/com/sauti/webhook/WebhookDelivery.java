package com.sauti.webhook;

import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String payloadJson;

    @Column(nullable = false)
    private String endpointUrl;

    @Column(nullable = false)
    private int attemptCount = 0;

    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime lastAttemptAt;
    private Integer lastStatusCode;

    @Column(nullable = false)
    private boolean success = false;

    private String failureReason;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(Tenant tenant, String eventType, String payloadJson, String endpointUrl) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.endpointUrl = endpointUrl;
        this.nextAttemptAt = OffsetDateTime.now();
    }

    public void markSuccess(int statusCode) {
        this.success = true;
        this.lastStatusCode = statusCode;
        this.lastAttemptAt = OffsetDateTime.now();
        this.nextAttemptAt = null;
        this.failureReason = null;
    }

    public void markFailure(Integer statusCode, String reason, OffsetDateTime nextAttemptAt) {
        this.attemptCount++;
        this.lastStatusCode = statusCode;
        this.lastAttemptAt = OffsetDateTime.now();
        this.nextAttemptAt = nextAttemptAt;
        this.failureReason = reason == null ? "Webhook delivery failed" : reason;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public boolean isSuccess() {
        return success;
    }
}
