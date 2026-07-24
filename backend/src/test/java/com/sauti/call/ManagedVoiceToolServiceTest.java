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
import com.sauti.session.PendingAction;
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
                .containsEntry("success", true)
                .containsEntry("workflowPending", true)
                .containsEntry("actionPerformed", false)
                .containsEntry("status", "action_deferred")
                .doesNotContainKey("error");
        assertThat(result.get("instruction").toString()).contains("do not claim success");
    }

    @Test
    void authenticatedBrowserClientUsesTheManagedEnvelopeAndExposesNextToolFacts() {
        var repository = mock(CallRepository.class);
        var router = mock(ToolFulfillmentRouter.class);
        var sessions = mock(CallSessionStore.class);
        var objectMapper = new ObjectMapper();
        var tokenService = new WebVoiceTokenService(
                "managed-voice-test-secret-managed-voice-test-secret", 10
        );
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getId()).thenReturn(UUID.randomUUID());
        when(call.getTwilioCallSid()).thenReturn("client-call");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of(
                    "status", "booking_confirmation_required",
                    "actionPerformed", false,
                    "nextTool", "book_slot",
                    "nextToolAuthorized", true,
                    "nextToolArguments", Map.of("review_token", "opaque"),
                    "instruction", "Continue with the authorized next tool."
            ));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);

        var result = service.executeAuthenticated(
                "elevenlabs",
                call,
                "provider-tool-1",
                "update_conversation_state",
                "{\"review_decision\":\"confirmed\",\"unused\":null}"
        );

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("workflowPending", true)
                .containsEntry("actionPerformed", false)
                .containsEntry("nextTool", "book_slot")
                .containsEntry("nextToolAuthorized", true);
        assertThat(result.get("nextToolArguments")).isEqualTo(Map.of("review_token", "opaque"));
        var routed = ArgumentCaptor.forClass(com.sauti.llm.LlmToolCall.class);
        verify(router).route(any(), routed.capture());
        assertThat(routed.getValue().arguments())
                .containsEntry("review_decision", "confirmed")
                .doesNotContainKey("unused");
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
        when(sessions.pendingAction("call-later-confirm")).thenReturn(Optional.of(
                new PendingAction(
                        "cancel_booking",
                        Map.of("booking_number", "SAT-AB12CD34", "caller_phone", "0115752441"),
                        4
                )
        ));
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

    @Test
    void executesTheServerRetainedRescheduleWhenProviderConfirmationArgumentsDrift() throws Exception {
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
        var retainedArguments = Map.<String, Object>of(
                "booking_number", "SAT-AB12CD34",
                "caller_phone", "0115752441",
                "appointment_at", "2026-08-03T10:00:00+03:00",
                "duration_minutes", 60
        );
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("call-reschedule-confirm");
        when(call.getDirection()).thenReturn("test");
        when(call.isActive()).thenReturn(true);
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(repository.findByTwilioCallSid("call-reschedule-confirm"))
                .thenReturn(Optional.of(call));
        when(sessions.pendingAction("call-reschedule-confirm")).thenReturn(Optional.of(
                new PendingAction("reschedule_booking", retainedArguments, 9)
        ));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of(
                    "status", "booking_rescheduled",
                    "actionPerformed", true,
                    "updated", true
            ));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);
        var token = tokenService.issue("call-reschedule-confirm", agentId.toString());
        var payload = objectMapper.readTree("""
                {
                  "name": "reschedule_booking",
                  "tool_call_id": "provider-confirmation",
                  "args": {
                    "booking_number": "SAT-AB12CD34",
                    "appointment_at": "2026-08-03T10:00:00",
                    "question_handling": "ready_for_action",
                    "confirmation_state": "confirmed"
                  }
                }
                """);

        var result = service.execute("telnyx", "call-reschedule-confirm", token, payload);

        verify(sessions).recordManagedConfirmation(
                "call-reschedule-confirm", "reschedule_booking", retainedArguments
        );
        var routed = ArgumentCaptor.forClass(com.sauti.llm.LlmToolCall.class);
        verify(router).route(any(), routed.capture());
        assertThat(routed.getValue().arguments()).containsAllEntriesOf(retainedArguments);
        assertThat(routed.getValue().arguments())
                .containsEntry("question_handling", "ready_for_action")
                .containsEntry("confirmation_state", "confirmed")
                .doesNotContainEntry("appointment_at", "2026-08-03T10:00:00");
        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("actionPerformed", true)
                .containsEntry("status", "booking_rescheduled");
    }
}
