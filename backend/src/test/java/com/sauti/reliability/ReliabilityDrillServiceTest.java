package com.sauti.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.admin.PlatformAdminAuditService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ReliabilityDrillServiceTest {
    private final ReliabilityDrillRepository drills = mock(ReliabilityDrillRepository.class);
    private final ReliabilityIncidentRepository incidents = mock(ReliabilityIncidentRepository.class);
    private final PlatformAdminAuditService audit = mock(PlatformAdminAuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ReliabilityDrillService service = new ReliabilityDrillService(drills, incidents, audit, events);

    @Test
    void startsAConfirmedSyntheticIncidentAndPublishesTheDetectionAlert() {
        when(incidents.save(any(ReliabilityIncident.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(drills.save(any(ReliabilityDrill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var view = service.start("admin@sauti.uk", ReliabilityDrillService.START_CONFIRMATION);

        assertThat(view.status()).isEqualTo("detected");
        var event = ArgumentCaptor.forClass(ReliabilityAlertRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().provider()).isEqualTo("drill:" + view.id());
        assertThat(event.getValue().severity()).isEqualTo("critical");
        verify(audit).record("admin@sauti.uk", "reliability.drill.started", "reliability_drill",
                view.id().toString(),
                "Started a synthetic critical incident; no customer or provider operation was changed");
    }

    @Test
    void refusesMissingConfirmationAndConcurrentDrills() {
        assertThatThrownBy(() -> service.start("admin@sauti.uk", "start"))
                .isInstanceOf(IllegalArgumentException.class);
        when(drills.existsByStatusIn(List.of("detected", "acknowledged"))).thenReturn(true);
        assertThatThrownBy(() -> service.start("admin@sauti.uk", ReliabilityDrillService.START_CONFIRMATION))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresNotificationThenAcknowledgementBeforeRecovery() {
        var incident = new ReliabilityIncident("drill:test", "critical", "Synthetic drill", OffsetDateTime.now());
        var drill = new ReliabilityDrill(java.util.UUID.randomUUID(), incident.getId(),
                "starter@sauti.uk", OffsetDateTime.now());
        when(drills.findById(drill.getId())).thenReturn(Optional.of(drill));
        when(incidents.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(drills.save(drill)).thenReturn(drill);
        when(incidents.save(incident)).thenReturn(incident);

        assertThatThrownBy(() -> service.acknowledge(drill.getId(), "operator@sauti.uk"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("detection email");
        incident.markNotificationSent(false, OffsetDateTime.now());
        assertThat(service.acknowledge(drill.getId(), "operator@sauti.uk").status())
                .isEqualTo("acknowledged");

        var resolved = service.resolve(drill.getId(), "operator@sauti.uk");

        assertThat(resolved.status()).isEqualTo("resolved");
        assertThat(incident.getStatus()).isEqualTo("resolved");
        var event = ArgumentCaptor.forClass(ReliabilityAlertRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().recovery()).isTrue();
    }
}
