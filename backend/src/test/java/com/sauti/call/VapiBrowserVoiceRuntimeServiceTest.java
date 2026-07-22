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
        when(agent.getDefaultLanguage()).thenReturn("fr");
        when(agent.getTtsVoiceId()).thenReturn("cartesia:voice-Amina");
        when(agent.getSupportedLanguages()).thenReturn(List.of("fr"));
        when(agent.getSystemPrompt()).thenReturn("Business: Clinique Amina");
        when(agent.getSttBoostedKeywords()).thenReturn("Sauti, consultation premium, Sauti");
        when(agent.getSttEndpointingMs()).thenReturn(340);
        when(agent.getMaxCallDurationSeconds()).thenReturn(720);
        when(orchestrator.realtimeInstructions(call, "fr")).thenReturn(
                "Answer in French, use tools safely, and call end_call after the farewell."
        );
        when(loader.loadForAgent(agentId)).thenReturn(List.of(
                new LlmToolDefinition(
                        "update_customer_record", "Update a confirmed record",
                        Map.of("type", "object", "properties", Map.of(
                                "customerId", Map.of("type", "string", "format", "uuid"),
                                "phone", Map.of("type", "string", "format", "phone"),
                                "email", Map.of("type", "string", "format", "email"),
                                "contact", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "mobile", Map.of("type", "string", "format", "phone")
                                        )
                                )
                        )),
                        true
                ),
                new LlmToolDefinition("end_call", "End after a farewell", Map.of("type", "object"))
        ));
        var service = new VapiBrowserVoiceRuntimeService(
                orchestrator, loader, "vapi-public-key", "https://sauti.uk/",
                "openai", "gpt-4.1-mini", "deepgram", "agent", "agent", 0.7, 2500,
                "vapi", "Savannah", 2, "auto", "sonic-3", 30, 1600
        );

        var session = service.prepare(call, "Bonjour, comment puis-je vous aider ?", "browser/call-token");
        var json = new ObjectMapper().writeValueAsString(session.configuration());

        assertThat(session.provider()).isEqualTo("vapi");
        assertThat(session.clientToken()).isEqualTo("browser/call-token");
        assertThat(session.apiBaseUrl()).isEqualTo("/api/v1/public/vapi/test-call%2F42");
        assertThat(json)
                .contains("Bonjour, comment puis-je vous aider ?")
                .contains("Answer in French, use tools safely, and call endCall after the farewell.")
                .contains("update_customer_record")
                .contains("\"type\":\"endCall\"")
                .contains("request-start")
                .contains("request-response-delayed")
                .contains("\"timingMilliseconds\":1600")
                .contains("\"minCharacters\":80")
                .contains("\"model\":\"flux-general-multi\"")
                .contains("\"eotThreshold\":0.7")
                .contains("\"eotTimeoutMs\":2500")
                .contains("\"startSpeakingPlan\":{\"waitSeconds\":0.1}")
                .contains("\"modelOutputInMessagesEnabled\":true")
                .contains("assistant.speechStarted")
                .contains("voice-input")
                .contains("end-of-call-report")
                .contains("\"format\":\"uuid\"")
                .contains("\"format\":\"email\"")
                .contains("https://sauti.uk/api/v1/public/vapi/test-call%2F42/webhook?token=browser%2Fcall-token")
                .contains("\"provider\":\"deepgram\"")
                .contains("\"language\":\"multi\"")
                .contains("\"keyterm\":[\"Clinique Amina\",\"Sauti\",\"consultation premium\"]")
                .contains("\"provider\":\"cartesia\"")
                .contains("\"voiceId\":\"voice-Amina\"")
                .contains("\"model\":\"sonic-3\"")
                .doesNotContain("\"name\":\"end_call\"")
                .doesNotContain("onNoPunctuationSeconds")
                .doesNotContain("\"format\":\"phone\"")
                .doesNotContain("vapi-public-key");
        assertThat(service.claimWebCall("test-call/42", "browser/call-token"))
                .isEqualTo(session.configuration());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.claimWebCall("test-call/42", "browser/call-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsARealtimeSpeechToSpeechAssistantWithoutCascadeStages() throws Exception {
        var orchestrator = mock(ConversationOrchestrator.class);
        var loader = mock(AgentToolLoader.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var callId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("test-realtime-42");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getName()).thenReturn("Amina");
        when(agent.getDefaultLanguage()).thenReturn("en");
        when(agent.getSupportedLanguages()).thenReturn(List.of("en"));
        when(agent.getMaxCallDurationSeconds()).thenReturn(720);
        when(orchestrator.realtimeInstructions(call, "en")).thenReturn("Help the caller and use tools safely.");
        when(loader.loadForAgent(agentId)).thenReturn(List.of());
        var service = new VapiBrowserVoiceRuntimeService(
                orchestrator, loader, "vapi-public-key", "https://sauti.uk",
                "openai", "gpt-realtime-2025-08-28", "deepgram", "nova-3", "agent", 0.7, 2500,
                "vapi", "Savannah", 2, "auto", "sonic-3", 30, 1000
        );

        var session = service.prepare(call, "Hello, how can I help?", "browser-token");
        var json = new ObjectMapper().writeValueAsString(session.configuration());

        assertThat(json)
                .contains("\"model\":\"gpt-realtime-2025-08-28\"")
                .contains("\"temperature\":0.6")
                .contains("\"maxTokens\":300")
                .contains("\"voice\":{\"provider\":\"openai\",\"voiceId\":\"marin\"}")
                .contains("\"startSpeakingPlan\":{\"waitSeconds\":0.1}")
                .doesNotContain("\"transcriber\"")
                .doesNotContain("chunkPlan")
                .doesNotContain("cachingEnabled");
    }

    @Test
    void fallsBackToNovaForLanguagesFluxDoesNotSupport() throws Exception {
        var orchestrator = mock(ConversationOrchestrator.class);
        var loader = mock(AgentToolLoader.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(UUID.randomUUID());
        when(call.getTwilioCallSid()).thenReturn("test-arabic-42");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getName()).thenReturn("Amina");
        when(agent.getDefaultLanguage()).thenReturn("ar");
        when(agent.getSupportedLanguages()).thenReturn(List.of("ar"));
        when(agent.getSttEndpointingMs()).thenReturn(340);
        when(agent.getMaxCallDurationSeconds()).thenReturn(720);
        when(orchestrator.realtimeInstructions(call, "ar")).thenReturn("Help in Arabic.");
        when(loader.loadForAgent(agentId)).thenReturn(List.of());
        var service = new VapiBrowserVoiceRuntimeService(
                orchestrator, loader, "vapi-public-key", "https://sauti.uk",
                "openai", "gpt-4.1-mini", "deepgram", "agent", "agent", 0.7, 2500,
                "vapi", "Savannah", 2, "auto", "sonic-3", 30, 1000
        );

        var json = new ObjectMapper().writeValueAsString(
                service.prepare(call, "مرحبا", "browser-token").configuration()
        );

        assertThat(json)
                .contains("\"model\":\"nova-3\"")
                .contains("\"language\":\"ar\"")
                .contains("\"endpointing\":340")
                .contains("onNoPunctuationSeconds")
                .doesNotContain("eotThreshold");
    }
}
