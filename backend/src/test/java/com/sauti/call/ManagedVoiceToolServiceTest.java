package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.integration.DuringCallIntegrationFulfillment;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.CallSession;
import com.sauti.session.CallSessionStore;
import com.sauti.session.PendingAction;
import com.sauti.session.RedisCallSessionStore;
import com.sauti.tool.AgentTool;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.ConversationStateTool;
import com.sauti.tool.NoopFulfillment;
import com.sauti.tool.SautiCalendarFulfillment;
import com.sauti.tool.SautiSmsFulfillment;
import com.sauti.tool.TelnyxTransferFulfillment;
import com.sauti.tool.ToolActionEffect;
import com.sauti.tool.ToolActionPolicy;
import com.sauti.tool.ToolConfirmationPolicy;
import com.sauti.tool.ToolFulfillmentRouter;
import com.sauti.tool.WebhookToolFulfillment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ManagedVoiceToolServiceTest {
    @Test
    void executesRescheduleAfterOneCallerApprovalWhenManagedSessionWasMissing() throws Exception {
        var callRepository = mock(CallRepository.class);
        var agentToolRepository = mock(AgentToolRepository.class);
        var calendar = mock(SautiCalendarFulfillment.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(null);
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var sessions = new RedisCallSessionStore(redis, objectMapper, 7200);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(UUID.randomUUID());
        when(call.getTwilioCallSid()).thenReturn("missing-session-reschedule");
        when(call.getAgent()).thenReturn(agent);
        when(call.isActive()).thenReturn(true);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(callRepository.findByTwilioCallSid("missing-session-reschedule"))
                .thenReturn(Optional.of(call));
        var tool = new AgentTool(
                agent,
                "reschedule_booking",
                "Reschedule a verified booking",
                Map.of("type", "object", "properties", Map.of()),
                "sauti_calendar",
                true,
                1
        );
        tool.configureActionPolicy(ToolActionEffect.DATA_WRITE, ToolConfirmationPolicy.EXPLICIT);
        when(agentToolRepository.findByAgent_IdAndToolNameAndIsActiveTrue(
                agentId, "reschedule_booking"
        )).thenReturn(Optional.of(tool));
        when(calendar.execute(any(), any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(2);
            return LlmToolResult.success(toolCall, Map.of(
                    "status", "booking_rescheduled",
                    "updated", true
            ));
        });
        var router = new ToolFulfillmentRouter(
                agentToolRepository,
                calendar,
                mock(WebhookToolFulfillment.class),
                mock(SautiSmsFulfillment.class),
                mock(TelnyxTransferFulfillment.class),
                mock(DuringCallIntegrationFulfillment.class),
                mock(NoopFulfillment.class),
                mock(ConversationStateTool.class),
                new ToolActionPolicy(sessions)
        );
        var service = new ManagedVoiceToolService(
                callRepository,
                mock(WebVoiceTokenService.class),
                router,
                sessions,
                objectMapper
        );
        var payload = objectMapper.readTree("""
                {
                  "booking_number": "SAT-AB12CD34",
                  "caller_phone": "0115752441",
                  "appointment_at": "2026-08-03T09:00:00+03:00",
                  "duration_minutes": 60,
                  "question_handling": "ready_for_action",
                  "confirmation_state": "confirmed"
                }
                """);

        var proposal = service.executeTelnyxWebhook(
                "missing-session-reschedule", "reschedule-proposal",
                "reschedule_booking", payload
        );
        var approved = service.executeTelnyxWebhook(
                "missing-session-reschedule", "reschedule-approved",
                "reschedule_booking", payload
        );

        assertThat(proposal)
                .containsEntry("status", "action_deferred")
                .containsEntry("actionPerformed", false);
        assertThat(approved)
                .containsEntry("status", "booking_rescheduled")
                .containsEntry("actionPerformed", true)
                .containsEntry("updated", true);
        verify(calendar, times(1)).execute(any(), any(), any());
    }

    @Test
    void telnyxWebhookExecutesDirectBodyArgumentsAndDeduplicatesRedelivery() throws Exception {
        var repository = mock(CallRepository.class);
        var router = mock(ToolFulfillmentRouter.class);
        var sessions = mock(CallSessionStore.class);
        var objectMapper = new ObjectMapper();
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getId()).thenReturn(UUID.randomUUID());
        when(call.getAgent()).thenReturn(agent);
        when(agent.getMaxCallDurationSeconds()).thenReturn(600);
        when(call.getTwilioCallSid()).thenReturn("telnyx-call-42");
        when(call.isActive()).thenReturn(true);
        when(repository.findByTwilioCallSid("telnyx-call-42")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var toolCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of(
                    "status", "availability_checked",
                    "available", true
            ));
        });
        var service = new ManagedVoiceToolService(
                repository,
                mock(WebVoiceTokenService.class),
                router,
                sessions,
                objectMapper
        );
        var body = objectMapper.readTree("""
                {"date":"2026-07-31","time":"10:00"}
                """);

        var first = service.executeTelnyxWebhook(
                "telnyx-call-42", "tool-call-1", "check_availability", body
        );
        var retry = service.executeTelnyxWebhook(
                "telnyx-call-42", "tool-call-1", "check_availability", body
        );

        assertThat(first)
                .containsEntry("success", true)
                .containsEntry("status", "availability_checked");
        assertThat(retry).isEqualTo(first);
        var routed = ArgumentCaptor.forClass(com.sauti.llm.LlmToolCall.class);
        verify(router, times(1)).route(any(), routed.capture());
        var recoveredSession = ArgumentCaptor.forClass(CallSession.class);
        verify(sessions).createIfAbsent(eq("telnyx-call-42"), recoveredSession.capture());
        assertThat(recoveredSession.getValue().getCallSid()).isEqualTo("telnyx-call-42");
        assertThat(recoveredSession.getValue().getCallId()).isEqualTo(call.getId());
        assertThat(routed.getValue().arguments())
                .containsEntry("date", "2026-07-31")
                .containsEntry("time", "10:00");
    }

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

        var first = service.execute("telnyx", "call-42", token, payload);
        var redelivery = service.execute("telnyx", "call-42", token, payload);

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

        service.execute("telnyx", "call-null", token, payload);

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

        var result = service.execute("telnyx", "call-confirm", token, payload);

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("workflowPending", true)
                .containsEntry("actionPerformed", false)
                .containsEntry("status", "action_deferred")
                .doesNotContainKey("error");
        assertThat(result.get("instruction").toString()).contains("do not claim success");
    }

    @Test
    void authenticatedBrowserClientFollowsAuthorizedNextToolAndUsesTheManagedEnvelope() {
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
            if ("book_slot".equals(toolCall.name())) {
                return LlmToolResult.success(toolCall, Map.of(
                        "status", "booking_confirmation_required",
                        "actionPerformed", false,
                        "instruction", "Read the verified booking review."
                ));
            }
            return LlmToolResult.success(toolCall, Map.of(
                    "nextTool", "book_slot",
                    "nextToolAuthorized", true,
                    "nextToolArguments", Map.of("review_token", "opaque"),
                    "instruction", "Continue with the authorized next tool."
            ));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);

        var result = service.executeAuthenticated(
                "telnyx",
                call,
                "provider-tool-1",
                "update_conversation_state",
                "{\"review_decision\":\"confirmed\",\"unused\":null}"
        );

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("workflowPending", true)
                .containsEntry("actionPerformed", false)
                .containsEntry("status", "booking_confirmation_required")
                .doesNotContainKeys("nextTool", "nextToolAuthorized", "nextToolArguments");
        var routed = ArgumentCaptor.forClass(com.sauti.llm.LlmToolCall.class);
        verify(router, times(2)).route(any(), routed.capture());
        assertThat(routed.getAllValues().get(0).arguments())
                .containsEntry("review_decision", "confirmed")
                .doesNotContainKey("unused");
        assertThat(routed.getAllValues().get(1).name()).isEqualTo("book_slot");
        assertThat(routed.getAllValues().get(1).arguments())
                .containsEntry("review_token", "opaque");
    }

    @Test
    void followsAnExactServerAuthorizedLookupWithoutDependingOnProviderReasoning() {
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
        when(call.getTwilioCallSid()).thenReturn("lookup-chain-call");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var routedCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            if ("update_conversation_state".equals(routedCall.name())) {
                return LlmToolResult.success(routedCall, Map.of(
                        "nextTool", "lookup_booking",
                        "nextToolAuthorized", true,
                        "nextToolArguments", Map.of(
                                "booking_number", "SAT-OHM2KFA6HOP1",
                                "caller_phone", "0115752441"
                        )
                ));
            }
            return LlmToolResult.success(routedCall, Map.of(
                    "status", "booking_found",
                    "bookingFound", true,
                    "bookingNumber", "SAT-OHM2KFA6HOP1"
            ));
        });
        var service = new ManagedVoiceToolService(repository, tokenService, router, sessions, objectMapper);

        var result = service.executeAuthenticated(
                "telnyx",
                call,
                "provider-state-1",
                "update_conversation_state",
                "{}"
        );

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("status", "booking_found")
                .containsEntry("bookingFound", true)
                .containsEntry("bookingNumber", "SAT-OHM2KFA6HOP1")
                .doesNotContainKeys("nextTool", "nextToolAuthorized", "nextToolArguments");
        var routed = ArgumentCaptor.forClass(com.sauti.llm.LlmToolCall.class);
        verify(router, times(2)).route(any(), routed.capture());
        assertThat(routed.getAllValues().get(1).name()).isEqualTo("lookup_booking");
        assertThat(routed.getAllValues().get(1).arguments()).isEqualTo(Map.of(
                "booking_number", "SAT-OHM2KFA6HOP1",
                "caller_phone", "0115752441"
        ));
    }

    @Test
    void exposesAuthoritativePhoneDigitsAtTheTopLevel() {
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
        when(call.getTwilioCallSid()).thenReturn("phone-digits-call");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getMaxCallDurationSeconds()).thenReturn(300);
        when(router.route(any(), any())).thenAnswer(invocation -> {
            var routedCall = (com.sauti.llm.LlmToolCall) invocation.getArgument(1);
            return LlmToolResult.success(routedCall, Map.of(
                    "status", "conversation_state_updated",
                    "phoneCaptureStatus", "complete",
                    "callerPhoneDigits",
                    List.of("0", "1", "1", "5", "7", "5", "2", "4", "4", "1")
            ));
        });
        var service = new ManagedVoiceToolService(
                repository, tokenService, router, sessions, objectMapper
        );

        var result = service.executeAuthenticated(
                "telnyx",
                call,
                "provider-phone-1",
                "update_conversation_state",
                "{}"
        );

        assertThat(result)
                .containsEntry("phoneCaptureStatus", "complete")
                .containsEntry(
                        "callerPhoneDigits",
                        List.of("0", "1", "1", "5", "7", "5", "2", "4", "4", "1")
                );
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
        when(sessions.takePendingAction("call-later-confirm", "cancel_booking")).thenReturn(Optional.of(
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

        verify(sessions).takePendingAction(
                "call-later-confirm",
                "cancel_booking"
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
        when(sessions.takePendingAction("call-reschedule-confirm", "reschedule_booking")).thenReturn(Optional.of(
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

        verify(sessions).takePendingAction(
                "call-reschedule-confirm", "reschedule_booking"
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
