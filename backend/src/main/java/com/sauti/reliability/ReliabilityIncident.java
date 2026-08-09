package com.sauti.reliability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "platform_reliability_incidents")
public class ReliabilityIncident {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String provider;
    @Column(nullable = false, length = 20) private String severity;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, length = 500) private String summary;
    @Column(nullable = false) private OffsetDateTime firstDetectedAt;
    @Column(nullable = false) private OffsetDateTime lastDetectedAt;
    private OffsetDateTime notifiedAt;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime resolutionNotifiedAt;

    protected ReliabilityIncident() { }

    public ReliabilityIncident(String provider, String severity, String summary, OffsetDateTime detectedAt) {
        this.id = UUID.randomUUID();
        this.provider = required(provider);
        this.severity = required(severity);
        this.status = "open";
        this.summary = required(summary);
        this.firstDetectedAt = detectedAt;
        this.lastDetectedAt = detectedAt;
    }

    public void observed(String severity, String summary, OffsetDateTime detectedAt) {
        if (!"open".equals(status)) throw new IllegalStateException("Only open incidents can be observed");
        this.severity = required(severity);
        this.summary = required(summary);
        this.lastDetectedAt = detectedAt;
    }

    public void resolve(OffsetDateTime resolvedAt) {
        if (!"open".equals(status)) return;
        this.status = "resolved";
        this.resolvedAt = resolvedAt;
    }

    public void markNotificationSent(boolean recovery, OffsetDateTime sentAt) {
        if (recovery) this.resolutionNotifiedAt = sentAt;
        else this.notifiedAt = sentAt;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getSummary() { return summary; }
    public OffsetDateTime getFirstDetectedAt() { return firstDetectedAt; }
    public OffsetDateTime getLastDetectedAt() { return lastDetectedAt; }
    public OffsetDateTime getNotifiedAt() { return notifiedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public OffsetDateTime getResolutionNotifiedAt() { return resolutionNotifiedAt; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Incident value is required");
        return value.trim();
    }
}
