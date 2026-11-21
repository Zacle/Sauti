package com.sauti.telnyx;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One Telnyx call-control event, stored before it is handled so it is handled exactly once, even
 * across a restart. Until it is finished it keeps its payload and a time to look at it again
 * ({@code nextAttemptAt}); once finished the payload is cleared, because it holds phone numbers.
 *
 * <p>Statuses: received, processing, failed (with a retry time while retries remain), completed,
 * expired (too late to act on).
 */
@Entity
@Table(name = "telnyx_webhook_events")
public class TelnyxWebhookEvent extends Auditable {
    /** If a received event has not started by then, the server stopped before handling it. */
    static final Duration START_WITHIN = Duration.ofSeconds(30);
    /** If processing has not finished by then, the server stopped while handling it. */
    static final Duration FINISH_WITHIN = Duration.ofMinutes(2);

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
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    protected TelnyxWebhookEvent() {
    }

    public TelnyxWebhookEvent(String providerEventId, String eventType, String callControlId, OffsetDateTime occurredAt) {
        this(providerEventId, eventType, callControlId, occurredAt, null);
    }

    public TelnyxWebhookEvent(
            String providerEventId,
            String eventType,
            String callControlId,
            OffsetDateTime occurredAt,
            String payloadJson
    ) {
        this.id = UUID.randomUUID();
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.callControlId = callControlId;
        this.occurredAt = occurredAt;
        this.payloadJson = payloadJson;
        this.status = "received";
        this.nextAttemptAt = payloadJson == null ? null : OffsetDateTime.now().plus(START_WITHIN);
    }

    public String getProviderEventId() { return providerEventId; }
    public String getEventType() { return eventType; }
    public String getCallControlId() { return callControlId; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getPayloadJson() { return payloadJson; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }

    /** When the event happened, or when it reached us if Telnyx did not say. */
    public OffsetDateTime happenedAt() {
        return occurredAt != null ? occurredAt : getCreatedAt();
    }

    /** Claims the event for a replay so the next scan does not dispatch it again meanwhile. */
    public void claimForReplay(OffsetDateTime now) {
        this.nextAttemptAt = now.plus(FINISH_WITHIN);
    }

    public void markProcessing() {
        this.status = "processing";
        this.attempts++;
        this.nextAttemptAt = payloadJson == null ? null : OffsetDateTime.now().plus(FINISH_WITHIN);
    }

    public void markCompleted() {
        this.status = "completed";
        this.failureReason = null;
        finish();
    }

    /** Failed for good: no retry is left. */
    public void markFailed(String reason) {
        markFailed(reason, null);
    }

    /** Failed this time; {@code retryAt} is when to try again, or null to give up. */
    public void markFailed(String reason, OffsetDateTime retryAt) {
        this.status = "failed";
        this.failureReason = reason == null ? "Unknown Telnyx webhook failure"
                : reason.substring(0, Math.min(1000, reason.length()));
        if (retryAt == null) finish();
        else this.nextAttemptAt = retryAt;
    }

    /** Too late to act on (the caller has gone); {@code reason} says why. */
    public void markExpired(String reason) {
        this.status = "expired";
        this.failureReason = reason;
        finish();
    }

    private void finish() {
        this.payloadJson = null;
        this.nextAttemptAt = null;
    }
}
