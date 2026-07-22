package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.tool.ToolFulfillmentRouter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VapiWebhookServiceTest {
    @Test
    void validatesTheCallAndRoutesVapiToolCallsThroughSautiPolicy() throws Exception {
        var repository = mock(CallRepository.class);
        var tokens = mock(WebVoiceTokenService.class);
        var router = mock(ToolFulfillmentRouter.class);
        var mapper = new ObjectMapper();
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(tokens.verify("call-token")).thenReturn(new WebVoiceTokenService.WebVoicePrincipal("test-42", agentId.toString()));
        when(repository.findByTwilioCallSid("test-42")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            LlmToolCall toolCall = invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of("status", "updated", "customerId", "cust-9"));
        });
        var service = new VapiWebhookService(repository, tokens, router, mapper);
        var payload = mapper.readTree("""
                {"message":{"type":"tool-calls","toolCallList":[{
                  "id":"tool-7","name":"update_customer_record",
                  "arguments":{"customerId":"cust-9","confirmation_state":"confirmed"}
                }]}}
                """);

        var response = service.handle("test-42", "call-token", payload);

        assertThat(mapper.writeValueAsString(response))
                .contains("tool-7")
                .contains("update_customer_record")
                .contains("updated");
        var toolCall = ArgumentCaptor.forClass(LlmToolCall.class);
        verify(router).route(any(), toolCall.capture());
        assertThat(toolCall.getValue().arguments())
                .containsEntry("customerId", "cust-9")
                .containsEntry("confirmation_state", "confirmed");
    }

    @Test
    void acceptsObjectArgumentsInsideTheOpenAiFunctionShape() throws Exception {
        var repository = mock(CallRepository.class);
        var tokens = mock(WebVoiceTokenService.class);
        var router = mock(ToolFulfillmentRouter.class);
        var mapper = new ObjectMapper();
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(tokens.verify("call-token")).thenReturn(
                new WebVoiceTokenService.WebVoicePrincipal("test-42", agentId.toString())
        );
        when(repository.findByTwilioCallSid("test-42")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            LlmToolCall toolCall = invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of("status", "available"));
        });
        var service = new VapiWebhookService(repository, tokens, router, mapper);

        service.handle("test-42", "call-token", mapper.readTree("""
                {"message":{"type":"tool-calls","toolCallList":[{
                  "id":"availability-1","function":{"name":"check_availability","arguments":{
                    "date":"2026-07-24","time_preference":"14:00"
                  }}
                }]}}
                """));

        var toolCall = ArgumentCaptor.forClass(LlmToolCall.class);
        verify(router).route(any(), toolCall.capture());
        assertThat(toolCall.getValue().arguments())
                .containsEntry("date", "2026-07-24")
                .containsEntry("time_preference", "14:00");
    }

    @Test
    void signalsTheClientOnlyAfterSautiAuthorizesAnEndCall() throws Exception {
        var repository = mock(CallRepository.class);
        var tokens = mock(WebVoiceTokenService.class);
        var router = mock(ToolFulfillmentRouter.class);
        var mapper = new ObjectMapper();
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(tokens.verify("call-token")).thenReturn(new WebVoiceTokenService.WebVoicePrincipal("test-42", agentId.toString()));
        when(repository.findByTwilioCallSid("test-42")).thenReturn(Optional.of(call));
        when(router.route(any(), any())).thenAnswer(invocation -> {
            LlmToolCall toolCall = invocation.getArgument(1);
            return LlmToolResult.success(toolCall, Map.of("ended", true));
        });
        var service = new VapiWebhookService(repository, tokens, router, mapper);

        var response = service.handle("test-42", "call-token", mapper.readTree("""
                {"message":{"type":"tool-calls","toolCallList":[{
                  "id":"end-1","function":{"name":"end_call","arguments":"{}"}
                }]}}
                """));

        assertThat(mapper.writeValueAsString(response)).contains("sautiEndCall").contains("true");
    }
}
