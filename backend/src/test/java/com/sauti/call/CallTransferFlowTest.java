package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.TelephonyProvider;
import com.sauti.llm.LlmToolCall;
import com.sauti.tenant.Tenant;
import com.sauti.tool.AgentTool;
import com.sauti.tool.TelnyxTransferFulfillment;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** A confirmed transfer dials the team member, and the result of that leg is what the call records. */
class CallTransferFlowTest {
    private CallRepository calls;
    private TelephonyProvider telephony;
    private CallTransferService transfers;
    private Call call;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        calls = mock(CallRepository.class);
        telephony = mock(TelephonyProvider.class);
        ObjectProvider<TelephonyProvider> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(telephony);
        transfers = new CallTransferService(calls, provider);
        var agent = mock(Agent.class);
        when(agent.getHumanTransferNumber()).thenReturn("+254711000000");
        when(agent.getTwilioPhoneNumber()).thenReturn("+254700000001");
        call = new Call(new Tenant("Clinic", "owner@example.com", "KE"), agent, "v3:caller-leg", "+254722000000", "inbound");
        when(calls.findById(call.getId())).thenReturn(Optional.of(call));
        when(calls.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aConfirmedTransferDialsTheTeamMemberTaggedWithTheCall() {
        var result = new TelnyxTransferFulfillment(transfers).execute(call, mock(AgentTool.class),
                new LlmToolCall("telnyx:webhook:t1", "transfer_to_human", Map.of("reason", "billing question")));

        assertThat(result.result()).containsEntry("transferPending", true);
        verify(telephony, timeout(2000)).transfer("v3:caller-leg", "+254711000000", "+254700000001",
                CallTransferService.CLIENT_STATE_PREFIX + call.getId());
        assertThat(CallTransferService.transferCallId(CallTransferService.CLIENT_STATE_PREFIX + call.getId()))
                .contains(call.getId());
        assertThat(CallTransferService.transferCallId("sauti-closed")).isEmpty();
    }

    @Test
    void aBrowserTestOnlySimulatesTheTransfer() {
        var test = new Call(call.getTenant(), call.getAgent(), "test-leg", "Browser test", "test");

        var result = new TelnyxTransferFulfillment(transfers).execute(test, mock(AgentTool.class),
                new LlmToolCall("telnyx:client:t2", "transfer_to_human", Map.of()));

        assertThat(result.result()).containsEntry("simulated", true);
        verify(telephony, never()).transfer(any(), any(), any(), any());
    }

    @Test
    void theTeamMembersLegEndingAfterAnsweringIsNotAFailure() {
        call.requestTransfer("+254711000000");

        transfers.handleDialResult(call.getId(), "answered", "v3:team-leg");
        transfers.handleDialResult(call.getId(), "failed", "v3:team-leg");

        assertThat(call.getTransferStatus()).isEqualTo("completed");
    }

    @Test
    void anUnansweredTransferSaysWhy() {
        call.requestTransfer("+254711000000");

        transfers.handleDialResult(call.getId(), "busy", "v3:team-leg");

        assertThat(call.getTransferStatus()).isEqualTo("busy");
        assertThat(call.getTransferFailureReason()).isEqualTo("The team member's line was busy");
    }

    @Test
    void callIdsFromOtherClientStatesAreIgnored() {
        assertThat(CallTransferService.transferCallId(CallTransferService.CLIENT_STATE_PREFIX + "not-a-uuid")).isEmpty();
        assertThat(CallTransferService.transferCallId(null)).isEmpty();
        assertThat(CallTransferService.transferCallId(CallTransferService.CLIENT_STATE_PREFIX + UUID.randomUUID()))
                .isPresent();
    }
}
