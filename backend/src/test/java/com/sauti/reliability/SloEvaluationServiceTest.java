package com.sauti.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.call.CallRepository;
import com.sauti.call.CallTurnRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SloEvaluationServiceTest {
    private final QueueHealthService queues = mock(QueueHealthService.class);
    private final CallRepository calls = mock(CallRepository.class);
    private final CallTurnRepository turns = mock(CallTurnRepository.class);
    private final SloEvaluationService service = new SloEvaluationService(
            queues, calls, turns, 5, 30, 15, 5, 10, 25, 10, 2500, 5000);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-08-09T12:00:00Z");

    @Test
    void exhaustedWorkIsImmediatelyCritical() {
        when(queues.snapshot()).thenReturn(List.of(new QueueHealthContributor.QueueState(
                "calendar_sync", "Calendar synchronization", 0, 0, 1, null)));
        noCallSamples();

        var queue = service.snapshot(now).stream().filter(slo -> slo.key().startsWith("queue:")).findFirst().orElseThrow();

        assertThat(queue.status()).isEqualTo("critical");
        assertThat(queue.detail()).contains("operator action");
    }

    @Test
    void staleWorkCrossesTheConfiguredAgeThreshold() {
        when(queues.snapshot()).thenReturn(List.of(new QueueHealthContributor.QueueState(
                "post_call", "Post-call processing", 1, 0, 0, now.minusMinutes(7))));
        noCallSamples();

        var queue = service.snapshot(now).stream().filter(slo -> slo.key().startsWith("queue:")).findFirst().orElseThrow();

        assertThat(queue.status()).isEqualTo("warning");
        assertThat(queue.actual()).isEqualTo(7);
    }

    @Test
    void oneFailedCallCannotCreateAPlatformSloBreach() {
        when(queues.snapshot()).thenReturn(List.of());
        when(calls.countCompletedProductionCallsStartedSince(now.minusMinutes(15))).thenReturn(1L);
        when(calls.countFailedProductionCallsStartedSince(now.minusMinutes(15))).thenReturn(1L);
        latency(0, 0);

        var failure = service.snapshot(now).stream().filter(slo -> slo.key().equals("calls:failure_rate")).findFirst().orElseThrow();

        assertThat(failure.status()).isEqualTo("insufficient_data");
        assertThat(failure.actual()).isEqualTo(100);
    }

    @Test
    void sustainedCallFailuresAndSlowResponsesAreCritical() {
        when(queues.snapshot()).thenReturn(List.of());
        when(calls.countCompletedProductionCallsStartedSince(now.minusMinutes(15))).thenReturn(10L);
        when(calls.countFailedProductionCallsStartedSince(now.minusMinutes(15))).thenReturn(3L);
        latency(12, 5500);

        var snapshot = service.snapshot(now);

        assertThat(snapshot).filteredOn(slo -> slo.status().equals("critical"))
                .extracting(SloEvaluationService.SloView::key)
                .containsExactlyInAnyOrder("calls:failure_rate", "voice:response_latency");
    }

    private void noCallSamples() {
        when(calls.countCompletedProductionCallsStartedSince(now.minusMinutes(15))).thenReturn(0L);
        when(calls.countFailedProductionCallsStartedSince(now.minusMinutes(15))).thenReturn(0L);
        latency(0, 0);
    }

    private void latency(long sampleSize, double averageMs) {
        var latency = mock(CallTurnRepository.PlatformResponseLatency.class);
        when(latency.getSampleSize()).thenReturn(sampleSize);
        when(latency.getAvgResponseMs()).thenReturn(averageMs);
        when(turns.platformResponseLatencySince(now.minusMinutes(15))).thenReturn(latency);
    }
}
