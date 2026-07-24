package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.CallSessionStore;
import com.sauti.tool.ToolFulfillmentRouter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManagedVoiceToolServiceTest {
    @Test
    void authorizesTheBoundAgentAndDeduplicatesProviderRedelivery() throws Exception {
        var repository = mock(CallRepository.class);
        var router = mock(ToolFulfillmentRouter.class);
        var sessions = mock(CallSessionStore.class);
        var objectMapper = new ObjectMapper();
        var tokenService = new WebVoiceTokenService(
                "managed-voice-test-secret-managed-voice-test-secret", 10
        );
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var callId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("call-42");
        when(call.getDirection()).thenReturn("test");
        when(call.isActive()).thenReturn(true);
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getWebVoicePublicId()).thenReturn("public-agent");
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(repository.findByTwilioCallSid("call-42")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of("available", true));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);
        var token = tokenService.issue("call-42", agentId.toString());
        var payload = objectMapper.readTree("""
                {
                  "name": "check_availability",
                  "args": {"date": "2026-07-31"},
                  "call": {"call_id": "provider-call-1"}
                }
                """);

        var first = service.execute("retell", "call-42", token, payload);
        var redelivery = service.execute("retell", "call-42", token, payload);

        assertThat(first).isEqualTo(Map.of(
                "success", true,
                "data", Map.of("available", true)
        ));
        assertThat(redelivery).isEqualTo(first);
        verify(router, times(1)).route(any(), any());
    }

    @Test
    void removesProviderNullsBeforeRoutingOptionalToolArguments() throws Exception {
        var repository = mock(CallRepository.class);
        var router = mock(ToolFulfillmentRouter.class);
        var sessions = mock(CallSessionStore.class);
        var objectMapper = new ObjectMapper();
        var tokenService = new WebVoiceTokenService(
                "managed-voice-test-secret-managed-voice-test-secret", 10
        );
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var callId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("call-null");
        when(call.getDirection()).thenReturn("test");
        when(call.isActive()).thenReturn(true);
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(repository.findByTwilioCallSid("call-null")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of("available", true));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);
        var token = tokenService.issue("call-null", agentId.toString());
        var payload = objectMapper.readTree("""
                {
                  "name": "check_availability",
                  "args": {
                    "date": "2026-07-31",
                    "duration_minutes": null,
                    "customer_details": {"phone": null}
                  }
                }
                """);

        service.execute("retell", "call-null", token, payload);

        var routed = ArgumentCaptor.forClass(com.sauti.llm.LlmToolCall.class);
        verify(router).route(any(), routed.capture());
        assertThat(routed.getValue().arguments()).isEqualTo(Map.of(
                "date", "2026-07-31",
                "customer_details", Map.of()
        ));
    }

    @Test
    void makesADeferredMutationOutcomeUnmistakable() throws Exception {
        var repository = mock(CallRepository.class);
        var router = mock(ToolFulfillmentRouter.class);
        var sessions = mock(CallSessionStore.class);
        var objectMapper = new ObjectMapper();
        var tokenService = new WebVoiceTokenService(
                "managed-voice-test-secret-managed-voice-test-secret", 10
        );
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var callId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("call-confirm");
        when(call.getDirection()).thenReturn("test");
        when(call.isActive()).thenReturn(true);
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(repository.findByTwilioCallSid("call-confirm")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of(
                    "status", "action_deferred",
                    "actionPerformed", false,
                    "instruction", "Ask once for confirmation and do not claim success."
            ));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);
        var token = tokenService.issue("call-confirm", agentId.toString());
        var payload = objectMapper.readTree("""
                {
                  "name": "cancel_booking",
                  "tool_call_id": "initial-proposal",
                  "args": {
                    "booking_number": "SAT-AB12CD34",
                    "caller_phone": "0115752441",
                    "question_handling": "ready_for_action",
                    "confirmation_state": "not_confirmed"
                  }
                }
                """);

        var result = service.execute("elevenlabs", "call-confirm", token, payload);

        assertThat(result)
                .containsEntry("success", false)
                .containsEntry("actionPerformed", false)
                .containsEntry("status", "action_deferred")
                .containsEntry("error", "No external action was performed");
        assertThat(result.get("instruction").toString()).contains("do not claim success");
    }

    @Test
    void bridgesAnExactLaterManagedConfirmationBeforeRouting() throws Exception {
        var repository = mock(CallRepository.class);
        var router = mock(ToolFulfillmentRouter.class);
        var sessions = mock(CallSessionStore.class);
        var objectMapper = new ObjectMapper();
        var tokenService = new WebVoiceTokenService(
                "managed-voice-test-secret-managed-voice-test-secret", 10
        );
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var callId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("call-later-confirm");
        when(call.getDirection()).thenReturn("test");
        when(call.isActive()).thenReturn(true);
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(repository.findByTwilioCallSid("call-later-confirm")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of(
                    "status", "booking_cancelled",
                    "actionPerformed", true,
                    "cancelled", true
            ));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);
        var token = tokenService.issue("call-later-confirm", agentId.toString());
        var payload = objectMapper.readTree("""
                {
                  "name": "cancel_booking",
                  "tool_call_id": "later-confirmation",
                  "args": {
                    "booking_number": "SAT-AB12CD34",
                    "caller_phone": "0115752441",
                    "question_handling": "ready_for_action",
                    "confirmation_state": "confirmed"
                  }
                }
                """);

        var result = service.execute("telnyx", "call-later-confirm", token, payload);

        verify(sessions).recordManagedConfirmation(
                "call-later-confirm",
                "cancel_booking",
                Map.of("booking_number", "SAT-AB12CD34", "caller_phone", "0115752441")
        );
        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("actionPerformed", true)
                .containsEntry("status", "booking_cancelled");
    }
}
