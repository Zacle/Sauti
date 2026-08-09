package com.sauti.reliability;

import com.sauti.integration.PlatformIntegrationHealthService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReliabilityMonitoringService {
    private final PlatformIntegrationHealthService providerHealth;
    private final ReliabilityIncidentRepository incidents;
    private final SloEvaluationService slos;
    private final ApplicationEventPublisher events;
    private final boolean enabled;
    private final int lookbackHours;
    private final int warningGraceMinutes;

    public ReliabilityMonitoringService(
            PlatformIntegrationHealthService providerHealth,
            ReliabilityIncidentRepository incidents,
            SloEvaluationService slos,
            ApplicationEventPublisher events,
            @Value("${sauti.reliability.alerts.enabled:false}") boolean enabled,
            @Value("${sauti.reliability.alerts.lookback-hours:24}") int lookbackHours,
            @Value("${sauti.reliability.alerts.warning-grace-minutes:10}") int warningGraceMinutes) {
        this.providerHealth = providerHealth;
        this.incidents = incidents;
        this.slos = slos;
        this.events = events;
        this.enabled = enabled;
        this.lookbackHours = Math.max(1, lookbackHours);
        this.warningGraceMinutes = Math.max(1, warningGraceMinutes);
    }

    @Scheduled(fixedDelayString = "${sauti.reliability.alerts.poll-delay-ms:300000}")
    @Transactional
    public void poll() {
        if (enabled) evaluate(OffsetDateTime.now(ZoneOffset.UTC));
    }

    void evaluate(OffsetDateTime now) {
        var observations = new java.util.ArrayList<Observation>();
        for (var health : providerHealth.snapshot(now.minusHours(lookbackHours))) {
            if (!List.of("degraded", "attention").contains(health.status())) continue;
            var severity = "attention".equals(health.status()) ? "critical" : "warning";
            observations.add(new Observation(health.provider(), severity, summary(health)));
        }
        for (var slo : slos.snapshot(now)) {
            if (!slo.breached()) continue;
            observations.add(new Observation(slo.key(), slo.status(), slo.detail()));
        }

        var observedProviders = new HashSet<String>();
        for (var observation : observations) {
            observedProviders.add(observation.key());
            var open = incidents.findFirstByProviderAndStatusOrderByFirstDetectedAtDesc(
                    observation.key(), "open");
            var incident = open.orElseGet(() -> new ReliabilityIncident(
                    observation.key(), observation.severity(), observation.summary(), now));
            if (open.isPresent()) incident.observed(observation.severity(), observation.summary(), now);
            incident = incidents.save(incident);
            var warningMatured = !incident.getFirstDetectedAt().isAfter(now.minusMinutes(warningGraceMinutes));
            if (incident.getNotifiedAt() == null && ("critical".equals(observation.severity()) || warningMatured)) {
                publish(incident, false, now);
            }
        }

        for (var incident : incidents.findAllByStatus("open")) {
            if (observedProviders.contains(incident.getProvider())) continue;
            if (incident.getProvider().startsWith("drill:")) continue;
            incident.resolve(now);
            incidents.save(incident);
            if (incident.getNotifiedAt() != null && incident.getResolutionNotifiedAt() == null) {
                publish(incident, true, now);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<IncidentView> recentIncidents() {
        return incidents.findTop50ByOrderByFirstDetectedAtDesc().stream().map(IncidentView::from).toList();
    }

    @Transactional
    public void markNotificationSent(UUID incidentId, boolean recovery, OffsetDateTime sentAt) {
        incidents.findById(incidentId).ifPresent(incident -> incident.markNotificationSent(recovery, sentAt));
    }

    private void publish(ReliabilityIncident incident, boolean recovery, OffsetDateTime now) {
        events.publishEvent(new ReliabilityAlertRequested(incident.getId(), incident.getProvider(),
                incident.getSeverity(), incident.getSummary(), recovery, now));
    }

    private String summary(PlatformIntegrationHealthService.ProviderHealth health) {
        return "%d connection errors, %d failed deliveries, and %d deliveries retrying in the last %d hours"
                .formatted(health.connectionErrors(), health.failed(), health.retrying(), lookbackHours);
    }

    private record Observation(String key, String severity, String summary) { }

    public record IncidentView(UUID id, String provider, String severity, String status, String summary,
                               OffsetDateTime firstDetectedAt, OffsetDateTime lastDetectedAt,
                               OffsetDateTime notifiedAt, OffsetDateTime resolvedAt) {
        static IncidentView from(ReliabilityIncident incident) {
            return new IncidentView(incident.getId(), incident.getProvider(), incident.getSeverity(),
                    incident.getStatus(), incident.getSummary(), incident.getFirstDetectedAt(),
                    incident.getLastDetectedAt(), incident.getNotifiedAt(), incident.getResolvedAt());
        }
    }
}
