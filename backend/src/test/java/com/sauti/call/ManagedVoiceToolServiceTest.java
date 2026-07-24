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
        var service = new ManagedVoiceToolService(repository, tokenService, router, objectMapper);
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
        var service = new ManagedVoiceToolService(repository, tokenService, router, objectMapper);
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
}
