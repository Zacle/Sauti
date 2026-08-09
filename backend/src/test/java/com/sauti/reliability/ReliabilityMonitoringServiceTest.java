package com.sauti.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sauti.integration.PlatformIntegrationHealthService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ReliabilityMonitoringServiceTest {
    @Test
    void waitsForTheGracePeriodBeforeAlertingOnRetryingDeliveries() {
        var health = mock(PlatformIntegrationHealthService.class);
        var incidents = mock(ReliabilityIncidentRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var slos = mock(SloEvaluationService.class);
        var now = OffsetDateTime.parse("2026-08-09T12:00:00Z");
        var provider = new PlatformIntegrationHealthService.ProviderHealth(
                "google_sheets", "degraded", 1, 1, 0, 1, 0, 1, 0, now.minusMinutes(1));
        when(health.snapshot(now.minusHours(24))).thenReturn(List.of(provider));
        when(incidents.findFirstByProviderAndStatusOrderByFirstDetectedAtDesc("google_sheets", "open"))
                .thenReturn(Optional.empty());
        when(incidents.save(any(ReliabilityIncident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(incidents.findAllByStatus("open")).thenReturn(List.of());
        when(slos.snapshot(now)).thenReturn(List.of());

        new ReliabilityMonitoringService(health, incidents, slos, events, true, 24, 10).evaluate(now);

        verifyNoInteractions(events);
    }

    @Test
    void opensOneIncidentAndRequestsAnOperatorAlert() {
        var health = mock(PlatformIntegrationHealthService.class);
        var incidents = mock(ReliabilityIncidentRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var slos = mock(SloEvaluationService.class);
        var now = OffsetDateTime.parse("2026-08-09T12:00:00Z");
        var provider = new PlatformIntegrationHealthService.ProviderHealth(
                "google_calendar", "attention", 1, 1, 0, 2, 1, 0, 1, now.minusMinutes(2));
        when(health.snapshot(now.minusHours(24))).thenReturn(List.of(provider));
        when(incidents.findFirstByProviderAndStatusOrderByFirstDetectedAtDesc("google_calendar", "open"))
                .thenReturn(Optional.empty());
        when(incidents.save(any(ReliabilityIncident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(incidents.findAllByStatus("open")).thenReturn(List.of());
        when(slos.snapshot(now)).thenReturn(List.of());

        new ReliabilityMonitoringService(health, incidents, slos, events, true, 24, 10).evaluate(now);

        var event = ArgumentCaptor.forClass(ReliabilityAlertRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().provider()).isEqualTo("google_calendar");
        assertThat(event.getValue().severity()).isEqualTo("critical");
        assertThat(event.getValue().recovery()).isFalse();
    }

    @Test
    void resolvesAnOpenIncidentAndRequestsOneRecoveryAlert() {
        var health = mock(PlatformIntegrationHealthService.class);
        var incidents = mock(ReliabilityIncidentRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var slos = mock(SloEvaluationService.class);
        var now = OffsetDateTime.parse("2026-08-09T12:00:00Z");
        var incident = new ReliabilityIncident("google_calendar", "critical", "delivery failed", now.minusHours(1));
        incident.markNotificationSent(false, now.minusMinutes(55));
        when(health.snapshot(now.minusHours(24))).thenReturn(List.of());
        when(incidents.findAllByStatus("open")).thenReturn(List.of(incident));
        when(incidents.save(any(ReliabilityIncident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(slos.snapshot(now)).thenReturn(List.of());

        new ReliabilityMonitoringService(health, incidents, slos, events, true, 24, 10).evaluate(now);

        assertThat(incident.getStatus()).isEqualTo("resolved");
        assertThat(incident.getResolvedAt()).isEqualTo(now);
        var event = ArgumentCaptor.forClass(ReliabilityAlertRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().recovery()).isTrue();
    }

    @Test
    void opensAnIncidentForACriticalSloBreach() {
        var health = mock(PlatformIntegrationHealthService.class);
        var incidents = mock(ReliabilityIncidentRepository.class);
        var slos = mock(SloEvaluationService.class);
        var events = mock(ApplicationEventPublisher.class);
        var now = OffsetDateTime.parse("2026-08-09T12:00:00Z");
        when(health.snapshot(now.minusHours(24))).thenReturn(List.of());
        when(slos.snapshot(now)).thenReturn(List.of(new SloEvaluationService.SloView(
                "queue:calendar_sync", "Calendar synchronization delay", "critical", 31,
                "minutes", 5, 30, 1, 0, "Oldest active item has waited 31 minute(s)")));
        when(incidents.findFirstByProviderAndStatusOrderByFirstDetectedAtDesc(
                "queue:calendar_sync", "open")).thenReturn(Optional.empty());
        when(incidents.save(any(ReliabilityIncident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(incidents.findAllByStatus("open")).thenReturn(List.of());

        new ReliabilityMonitoringService(health, incidents, slos, events, true, 24, 10).evaluate(now);

        var event = ArgumentCaptor.forClass(ReliabilityAlertRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().provider()).isEqualTo("queue:calendar_sync");
        assertThat(event.getValue().severity()).isEqualTo("critical");
    }

    @Test
    void doesNotAutoResolveAnOperatorControlledDrill() {
        var health = mock(PlatformIntegrationHealthService.class);
        var incidents = mock(ReliabilityIncidentRepository.class);
        var slos = mock(SloEvaluationService.class);
        var events = mock(ApplicationEventPublisher.class);
        var now = OffsetDateTime.parse("2026-08-09T12:00:00Z");
        var incident = new ReliabilityIncident("drill:test", "critical", "Synthetic drill", now.minusMinutes(2));
        when(health.snapshot(now.minusHours(24))).thenReturn(List.of());
        when(slos.snapshot(now)).thenReturn(List.of());
        when(incidents.findAllByStatus("open")).thenReturn(List.of(incident));

        new ReliabilityMonitoringService(health, incidents, slos, events, true, 24, 10).evaluate(now);

        assertThat(incident.getStatus()).isEqualTo("open");
        verifyNoInteractions(events);
    }
}
