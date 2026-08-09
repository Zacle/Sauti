package com.sauti.reliability;

import com.sauti.admin.PlatformAdminAuditService;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReliabilityDrillService {
    public static final String START_CONFIRMATION = "START RELIABILITY DRILL";
    private final ReliabilityDrillRepository drills;
    private final ReliabilityIncidentRepository incidents;
    private final PlatformAdminAuditService audit;
    private final ApplicationEventPublisher events;

    public ReliabilityDrillService(
            ReliabilityDrillRepository drills,
            ReliabilityIncidentRepository incidents,
            PlatformAdminAuditService audit,
            ApplicationEventPublisher events
    ) {
        this.drills = drills;
        this.incidents = incidents;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public DrillView start(String actorEmail, String confirmation) {
        if (!START_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("Exact reliability drill confirmation is required");
        }
        if (drills.existsByStatusIn(List.of("detected", "acknowledged"))) {
            throw new IllegalStateException("Resolve the active reliability drill before starting another");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var drillId = UUID.randomUUID();
        var incident = incidents.save(new ReliabilityIncident(
                "drill:" + drillId,
                "critical",
                "Synthetic reliability drill. No provider, customer data, or production job is affected.",
                now
        ));
        var drill = drills.save(new ReliabilityDrill(drillId, incident.getId(), actorEmail, now));
        audit.record(actorEmail, "reliability.drill.started", "reliability_drill", drillId.toString(),
                "Started a synthetic critical incident; no customer or provider operation was changed");
        publish(incident, false, now);
        return view(drill, incident);
    }

    @Transactional
    public DrillView acknowledge(UUID drillId, String actorEmail) {
        var drill = required(drillId);
        var incident = incident(drill);
        if (incident.getNotifiedAt() == null) {
            throw new IllegalStateException("Wait for the detection email before acknowledging the drill");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        drill.acknowledge(actorEmail, now);
        drills.save(drill);
        audit.record(actorEmail, "reliability.drill.acknowledged", "reliability_drill", drillId.toString(),
                "Acknowledged the synthetic reliability incident after notification delivery");
        return view(drill, incident);
    }

    @Transactional
    public DrillView resolve(UUID drillId, String actorEmail) {
        var drill = required(drillId);
        var incident = incident(drill);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        drill.resolve(actorEmail, now);
        incident.resolve(now);
        drills.save(drill);
        incidents.save(incident);
        audit.record(actorEmail, "reliability.drill.resolved", "reliability_drill", drillId.toString(),
                "Resolved the synthetic reliability incident and requested recovery notification");
        publish(incident, true, now);
        return view(drill, incident);
    }

    @Transactional(readOnly = true)
    public List<DrillView> recent() {
        return drills.findTop20ByOrderByInitiatedAtDesc().stream()
                .map(drill -> view(drill, incident(drill))).toList();
    }

    private ReliabilityDrill required(UUID id) {
        return drills.findById(id).orElseThrow(() -> new EntityNotFoundException("Reliability drill not found"));
    }

    private ReliabilityIncident incident(ReliabilityDrill drill) {
        return incidents.findById(drill.getIncidentId())
                .orElseThrow(() -> new EntityNotFoundException("Reliability drill incident not found"));
    }

    private void publish(ReliabilityIncident incident, boolean recovery, OffsetDateTime now) {
        events.publishEvent(new ReliabilityAlertRequested(incident.getId(), incident.getProvider(),
                incident.getSeverity(), incident.getSummary(), recovery, now));
    }

    private DrillView view(ReliabilityDrill drill, ReliabilityIncident incident) {
        return new DrillView(drill.getId(), drill.getStatus(), drill.getInitiatedBy(), drill.getInitiatedAt(),
                drill.getAcknowledgedBy(), drill.getAcknowledgedAt(), drill.getResolvedBy(), drill.getResolvedAt(),
                incident.getNotifiedAt(), incident.getResolutionNotifiedAt());
    }

    public record DrillView(UUID id, String status, String initiatedBy, OffsetDateTime initiatedAt,
                            String acknowledgedBy, OffsetDateTime acknowledgedAt,
                            String resolvedBy, OffsetDateTime resolvedAt,
                            OffsetDateTime detectionEmailSentAt, OffsetDateTime recoveryEmailSentAt) { }
}
