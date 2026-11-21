package com.sauti.telnyx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.agent.TelnyxTelephonyProvider;
import com.sauti.billing.BillingAccessPolicy;
import com.sauti.billing.ProviderCostReconciliationService;
import com.sauti.call.CallPipelineService;
import com.sauti.call.CallQueryService;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Events the server stopped before handling are replayed, or closed out when the caller has gone. */
class TelnyxEventReplayTest {
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);
    private static final String CONTROL = "v3:control-9";
    private static final String RING = """
            {"call_control_id":"v3:control-9","to":"+254700000001","from":"+254711111111","direction":"incoming"}""";
    private static final String HANGUP = """
            {"call_control_id":"v3:control-9","hangup_cause":"normal_clearing","call_session_id":"s-1"}""";
    private TelnyxWebhookEventRepository events;
    private CallPipelineService pipeline;
    private TelnyxTelephonyProvider telnyx;
    private TelnyxCallControlService service;

    @BeforeEach
    void setUp() {
        events = mock(TelnyxWebhookEventRepository.class);
        pipeline = mock(CallPipelineService.class);
        telnyx = mock(TelnyxTelephonyProvider.class);
        service = new TelnyxCallControlService(new ObjectMapper(), events, pipeline, telnyx,
                mock(CallQueryService.class), mock(ProviderCostReconciliationService.class),
                mock(PilotProvisioningPolicyService.class), mock(BillingAccessPolicy.class), new SimpleMeterRegistry());
        service.useClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void aRingNobodyHandledIsRecordedAsMissedAndNotAnsweredLate() {
        var agent = mock(Agent.class);
        when(pipeline.inboundAgent("+254700000001")).thenReturn(Optional.of(agent));
        var ring = due(new TelnyxWebhookEvent("ring-1", "call.initiated", CONTROL, NOW.minusSeconds(45), RING));

        service.replayUnfinished();

        verify(pipeline).recordUnansweredInboundCall(agent, CONTROL, "+254711111111", "not_handled");
        verify(telnyx).reject(CONTROL);
        verify(pipeline, never()).startInboundCall(anyString(), anyString(), anyString());
        assertThat(ring.getStatus()).isEqualTo("expired");
        assertThat(ring.getPayloadJson()).isNull();
        assertThat(ring.getNextAttemptAt()).isNull();
    }

    @Test
    void aHangUpNobodyHandledIsReplayedAndItsPayloadThenCleared() {
        var hangup = due(new TelnyxWebhookEvent("hangup-1", "call.hangup", CONTROL, NOW.minusMinutes(10), HANGUP));

        service.replayUnfinished();

        verify(pipeline, timeout(2000)).completeActiveCall(CONTROL, "completed");
        verify(events, timeout(2000).times(3)).save(hangup);
        assertThat(hangup.getStatus()).isEqualTo("completed");
        assertThat(hangup.getPayloadJson()).isNull();
    }

    @Test
    void aFailedHangUpIsRetriedLaterThenGivenUp() {
        var hangup = new TelnyxWebhookEvent("hangup-2", "call.hangup", CONTROL, NOW, HANGUP);
        when(events.findByProviderEventId("hangup-2")).thenReturn(Optional.of(hangup));
        doThrow(new IllegalStateException("database unavailable")).when(pipeline).completeActiveCall(CONTROL, "completed");

        service.process("hangup-2", "call.hangup", json(HANGUP), NOW);

        assertThat(hangup.getStatus()).isEqualTo("failed");
        assertThat(hangup.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(hangup.getPayloadJson()).isNotNull();

        for (int attempt = 2; attempt <= TelnyxCallControlService.MAX_ATTEMPTS; attempt++) {
            service.process("hangup-2", "call.hangup", json(HANGUP), NOW);
        }

        assertThat(hangup.getAttempts()).isEqualTo(TelnyxCallControlService.MAX_ATTEMPTS);
        assertThat(hangup.getNextAttemptAt()).isNull();
        assertThat(hangup.getPayloadJson()).isNull();
    }

    @Test
    void aRingThatFailsIsTurnedAwayAtOnceRatherThanRetried() {
        var ring = new TelnyxWebhookEvent("ring-2", "call.initiated", CONTROL, NOW, RING);
        when(events.findByProviderEventId("ring-2")).thenReturn(Optional.of(ring));
        when(pipeline.inboundAgent(any())).thenThrow(new IllegalStateException("database unavailable"));

        service.process("ring-2", "call.initiated", json(RING), NOW);

        verify(telnyx).reject(CONTROL);
        assertThat(ring.getStatus()).isEqualTo("failed");
        assertThat(ring.getNextAttemptAt()).isNull();
    }

    @Test
    void aFinishedEventIsNeverHandledTwice() {
        var hangup = new TelnyxWebhookEvent("hangup-3", "call.hangup", CONTROL, NOW, HANGUP);
        hangup.markCompleted();
        when(events.findByProviderEventId("hangup-3")).thenReturn(Optional.of(hangup));

        service.process("hangup-3", "call.hangup", json(HANGUP), NOW);

        verify(pipeline, never()).completeActiveCall(anyString(), anyString());
    }

    private TelnyxWebhookEvent due(TelnyxWebhookEvent event) {
        when(events.findTop100ByNextAttemptAtBeforeOrderByNextAttemptAtAsc(any())).thenReturn(List.of(event));
        when(events.findByProviderEventId(event.getProviderEventId())).thenReturn(Optional.of(event));
        return event;
    }

    private static com.fasterxml.jackson.databind.JsonNode json(String body) {
        try {
            return new ObjectMapper().readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
