package com.sauti.telnyx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.agent.TelnyxTelephonyProvider;
import com.sauti.billing.BillingAccessPolicy;
import com.sauti.billing.ProviderCostReconciliationService;
import com.sauti.call.Call;
import com.sauti.call.CallPipelineService;
import com.sauti.call.CallQueryService;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import com.sauti.tenant.Tenant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Every inbound call is answered or turned away at once; none is left ringing. */
class TelnyxInboundCallHandlingTest {
    private static final String CONTROL = "v3:control-1";
    private final ObjectMapper json = new ObjectMapper();
    private TelnyxWebhookEventRepository events;
    private CallPipelineService pipeline;
    private TelnyxTelephonyProvider telnyx;
    private CallQueryService queries;
    private BillingAccessPolicy billing;
    private PilotProvisioningPolicyService policies;
    private TelnyxCallControlService service;
    private Agent agent;
    private Call call;
    private final UUID tenantId = UUID.randomUUID();
    private TelnyxWebhookEvent event;
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meters =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        events = mock(TelnyxWebhookEventRepository.class);
        pipeline = mock(CallPipelineService.class);
        telnyx = mock(TelnyxTelephonyProvider.class);
        queries = mock(CallQueryService.class);
        billing = mock(BillingAccessPolicy.class);
        policies = mock(PilotProvisioningPolicyService.class);
        service = new TelnyxCallControlService(json, events, pipeline, telnyx, queries,
                mock(ProviderCostReconciliationService.class), policies, billing, meters);
        event = new TelnyxWebhookEvent("event-1", "call.initiated", CONTROL, null);
        when(events.findByProviderEventId(anyString())).thenReturn(Optional.of(event));

        var tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);
        agent = mock(Agent.class);
        when(agent.getTenant()).thenReturn(tenant);
        when(agent.getAfterHoursBehavior()).thenReturn("answer");
        call = mock(Call.class);
        when(call.getTenant()).thenReturn(tenant);
        when(call.getId()).thenReturn(UUID.randomUUID());
        when(call.getAgent()).thenReturn(agent);
        when(pipeline.inboundAgent("+254700000001")).thenReturn(Optional.of(agent));
        when(pipeline.startInboundCall("+254700000001", CONTROL, "+254711111111")).thenReturn(call);
        when(queries.firstAgentResponse(any(), any())).thenReturn("Hello from the clinic.");
    }

    @Test
    void aNumberWithNoActiveAgentIsRejectedRightAway() throws Exception {
        when(pipeline.inboundAgent("+254700000001")).thenReturn(Optional.empty());

        initiated();

        verify(telnyx).reject(CONTROL);
        verify(pipeline, never()).startInboundCall(anyString(), anyString(), anyString());
        assertThat(meters.get("sauti.voice.inbound.pickup").tag("result", "no_agent").timer().count()).isEqualTo(1);
    }

    @Test
    void aBlockedWorkspaceIsRejectedAndRecordedWithoutStartingTheAgent() throws Exception {
        doThrow(new IllegalStateException("unpaid")).when(billing).requirePaidCommunication(tenantId);

        initiated();

        verify(pipeline).recordUnansweredInboundCall(agent, CONTROL, "+254711111111", "billing_blocked");
        verify(telnyx).reject(CONTROL);
        verify(pipeline, never()).startInboundCall(anyString(), anyString(), anyString());
    }

    @Test
    void duringOpeningHoursTheAssistantAnswersWithTheGreeting() throws Exception {
        initiated();

        verify(telnyx).answerInboundCall(call, CONTROL, "Hello from the clinic.", "open");
        assertThat(meters.get("sauti.voice.inbound.pickup").tag("result", "answered").timer().count()).isEqualTo(1);
    }

    @Test
    void afterHoursTheAssistantIsToldToTakeAMessage() throws Exception {
        when(call.isAfterHours()).thenReturn(true);
        when(agent.getAfterHoursBehavior()).thenReturn("take_message");

        initiated();

        verify(telnyx).answerInboundCall(call, CONTROL, "Hello from the clinic.", "after_hours_take_message");
    }

    @Test
    void whenClosedTheMessageIsSpokenWithoutStartingTheAssistant() throws Exception {
        when(call.isAfterHours()).thenReturn(true);
        when(agent.getAfterHoursBehavior()).thenReturn("closed");

        initiated();

        verify(telnyx).answerWithClosedMessage(call, CONTROL, "Hello from the clinic.");
        verify(telnyx, never()).answerInboundCall(any(), anyString(), anyString(), anyString());
    }

    @Test
    void theClosedMessageEndingHangsUp() throws Exception {
        var state = Base64.getEncoder().encodeToString(
                TelnyxTelephonyProvider.CLOSED_ANNOUNCEMENT.getBytes(StandardCharsets.UTF_8));

        service.process("event-2", "call.speak.ended",
                payload("{\"call_control_id\":\"" + CONTROL + "\",\"client_state\":\"" + state + "\"}"), null);

        verify(pipeline).completeActiveCall(CONTROL, "closed");
        verify(telnyx).hangup(CONTROL);
    }

    @Test
    void anAgentThatCannotStartIsRecordedAndTheCallerIsNotLeftWaiting() throws Exception {
        doThrow(new IllegalStateException("still preparing"))
                .when(telnyx).answerInboundCall(any(), anyString(), anyString(), anyString());
        doThrow(new IllegalStateException("already answered")).when(telnyx).reject(CONTROL);

        initiated();

        verify(pipeline).recordUnansweredInboundCall(agent, CONTROL, "+254711111111", "agent_unavailable");
        verify(telnyx).hangup(CONTROL);
    }

    @Test
    void eventsForOneCallShareALaneAndCallsSpreadAcrossLanes() {
        assertThat(TelnyxCallControlService.lane(CONTROL)).isEqualTo(TelnyxCallControlService.lane(CONTROL));
        var used = new HashSet<Integer>();
        for (int index = 0; index < 200; index++) used.add(TelnyxCallControlService.lane("v3:call-" + index));
        assertThat(used).hasSize(TelnyxCallControlService.LANES);
    }

    private void initiated() throws Exception {
        service.process("event-1", "call.initiated", payload("""
                {"call_control_id":"%s","to":"+254700000001","from":"+254711111111","direction":"incoming"}
                """.formatted(CONTROL)), null);
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getStatus()).isEqualTo("completed");
    }

    private JsonNode payload(String body) throws Exception {
        return json.readTree(body);
    }
}
