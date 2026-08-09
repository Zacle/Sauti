package com.sauti.reliability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "platform_reliability_drills")
public class ReliabilityDrill {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID incidentId;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, length = 254) private String initiatedBy;
    @Column(nullable = false) private OffsetDateTime initiatedAt;
    @Column(length = 254) private String acknowledgedBy;
    private OffsetDateTime acknowledgedAt;
    @Column(length = 254) private String resolvedBy;
    private OffsetDateTime resolvedAt;

    protected ReliabilityDrill() { }

    ReliabilityDrill(UUID id, UUID incidentId, String actorEmail, OffsetDateTime initiatedAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.status = "detected";
        this.initiatedBy = required(actorEmail);
        this.initiatedAt = initiatedAt;
    }

    void acknowledge(String actorEmail, OffsetDateTime at) {
        if (!"detected".equals(status)) {
            throw new IllegalStateException("Only a detected drill can be acknowledged");
        }
        status = "acknowledged";
        acknowledgedBy = required(actorEmail);
        acknowledgedAt = at;
    }

    void resolve(String actorEmail, OffsetDateTime at) {
        if (!"acknowledged".equals(status)) {
            throw new IllegalStateException("A drill must be acknowledged before it can be resolved");
        }
        status = "resolved";
        resolvedBy = required(actorEmail);
        resolvedAt = at;
    }

    UUID getId() { return id; }
    UUID getIncidentId() { return incidentId; }
    String getStatus() { return status; }
    String getInitiatedBy() { return initiatedBy; }
    OffsetDateTime getInitiatedAt() { return initiatedAt; }
    String getAcknowledgedBy() { return acknowledgedBy; }
    OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    String getResolvedBy() { return resolvedBy; }
    OffsetDateTime getResolvedAt() { return resolvedAt; }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Actor email is required");
        return value.trim();
    }
}
