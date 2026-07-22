package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.llm.LlmToolDefinition;
import com.sauti.tool.AgentToolLoader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VapiBrowserVoiceRuntimeServiceTest {
    @Test
    void buildsATransientAssistantWithoutExposingTheVapiPublicKey() throws Exception {
        var orchestrator = mock(ConversationOrchestrator.class);
        var loader = mock(AgentToolLoader.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var callId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("test-call/42");
        when(call.getAgent()).thenReturn(agent);
        when(call.getLanguageDetected()).thenReturn("fr");
        when(agent.getId()).thenReturn(agentId);
        when(agent.getName()).thenReturn("Amina");
        when(agent.getSttEndpointingMs()).thenReturn(340);
        when(agent.getMaxCallDurationSeconds()).thenReturn(720);
        when(orchestrator.realtimeInstructions(call, "fr")).thenReturn("Answer in French and use tools safely.");
        when(loader.loadForAgent(agentId)).thenReturn(List.of(new LlmToolDefinition(
                "update_customer_record", "Update a confirmed record",
                Map.of("type", "object", "properties", Map.of("customerId", Map.of("type", "string")))
        )));
        var service = new VapiBrowserVoiceRuntimeService(
                orchestrator, loader, "vapi-public-key", "https://sauti.uk/",
                "openai", "gpt-4.1-mini", "deepgram", "nova-3", "multi",
                "vapi", "Savannah", 2, "auto", 30, 1600
        );

        var session = service.prepare(call, "Bonjour, comment puis-je vous aider ?", "browser/call-token");
        var json = new ObjectMapper().writeValueAsString(session.configuration());

        assertThat(session.provider()).isEqualTo("vapi");
        assertThat(session.clientToken()).isEqualTo("browser/call-token");
        assertThat(session.apiBaseUrl()).isEqualTo("/api/v1/public/vapi/test-call%2F42");
        assertThat(json)
                .contains("Bonjour, comment puis-je vous aider ?")
                .contains("Answer in French and use tools safely.")
                .contains("update_customer_record")
                .contains("request-response-delayed")
                .contains("\"timingMilliseconds\":1600")
                .contains("https://sauti.uk/api/v1/public/vapi/test-call%2F42/webhook?token=browser%2Fcall-token")
                .contains("\"provider\":\"deepgram\"")
                .contains("\"voiceId\":\"Savannah\"")
                .doesNotContain("vapi-public-key");
        assertThat(service.claimWebCall("test-call/42", "browser/call-token"))
                .isEqualTo(session.configuration());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.claimWebCall("test-call/42", "browser/call-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
