package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.LlmToolDefinition;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManagedVoiceAgentProvisionersTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retellCreatesItsResponseEngineAndVoiceAgentFromTheSautiBlueprint() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        when(http.post(eq("Retell"), eq(URI.create("https://api.retellai.com/create-retell-llm")), any(), any()))
                .thenReturn(objectMapper.readTree("{\"llm_id\":\"llm-1\"}"));
        when(http.post(eq("Retell"), eq(URI.create("https://api.retellai.com/create-agent")), any(), any()))
                .thenReturn(objectMapper.readTree("{\"agent_id\":\"agent-1\",\"version\":0}"));
        var provisioner = new RetellManagedVoiceAgentProvisioner(
                http,
                objectMapper,
                "secret",
                "https://api.retellai.com/",
                "retell-Cimo",
                "gpt-4.1-mini"
        );

        var reference = provisioner.synchronize(blueprint(), null);

        assertThat(reference.externalAgentId()).isEqualTo("agent-1");
        assertThat(reference.externalResourcesJson()).contains("llm-1");
        @SuppressWarnings("unchecked")
        var body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(http).post(
                eq("Retell"),
                eq(URI.create("https://api.retellai.com/create-retell-llm")),
                any(),
                body.capture()
        );
        assertThat(body.getValue())
                .containsEntry("begin_message", "Hello from Sauti")
                .containsKey("general_tools");
        assertThat(body.getValue().toString()).contains("additionalProperties");
    }

    @Test
    void elevenLabsCreatesStandaloneToolsAndAttachesThemToTheGeneratedAgent() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        when(http.post(eq("ElevenLabs"), eq(URI.create("https://api.elevenlabs.io/v1/convai/tools")), any(), any()))
                .thenReturn(objectMapper.readTree("{\"id\":\"tool-1\"}"));
        when(http.post(
                eq("ElevenLabs"),
                eq(URI.create("https://api.elevenlabs.io/v1/convai/agents/create")),
                any(),
                any()
        )).thenReturn(objectMapper.readTree("{\"agent_id\":\"eleven-agent\"}"));
        var provisioner = new ElevenLabsManagedVoiceAgentProvisioner(
                http,
                objectMapper,
                "secret",
                "https://api.elevenlabs.io/"
        );

        var reference = provisioner.synchronize(blueprint(), null);

        assertThat(reference.externalAgentId()).isEqualTo("eleven-agent");
        assertThat(reference.externalResourcesJson()).contains("tool-1").doesNotContain("tool-2");
        @SuppressWarnings("unchecked")
        var toolBody = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(http, times(1)).post(
                eq("ElevenLabs"),
                eq(URI.create("https://api.elevenlabs.io/v1/convai/tools")),
                any(),
                toolBody.capture()
        );
        assertThat(toolBody.getValue().toString())
                .contains("check_availability")
                .doesNotContain("additionalProperties")
                .doesNotContain("maxLength")
                .doesNotContain("end_call");
        @SuppressWarnings("unchecked")
        var toolConfig = (Map<String, Object>) toolBody.getValue().get("tool_config");
        @SuppressWarnings("unchecked")
        var parameters = (Map<String, Object>) toolConfig.get("parameters");
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) parameters.get("properties");
        @SuppressWarnings("unchecked")
        var clearFields = (Map<String, Object>) properties.get("clear_fields");
        @SuppressWarnings("unchecked")
        var clearFieldItems = (Map<String, Object>) clearFields.get("items");
        assertThat(clearFieldItems)
                .containsEntry("type", "string")
                .containsEntry("description", "Value for clear fields item.");
        @SuppressWarnings("unchecked")
        var date = (Map<String, Object>) properties.get("date");
        assertThat(date)
                .containsEntry("description", "Appointment date. Expected format: date.")
                .doesNotContainKeys("format", "maxLength");
        @SuppressWarnings("unchecked")
        var agentBody = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(http).post(
                eq("ElevenLabs"),
                eq(URI.create("https://api.elevenlabs.io/v1/convai/agents/create")),
                any(),
                agentBody.capture()
        );
        assertThat(agentBody.getValue().toString())
                .contains("Hello from Sauti")
                .contains("tool_ids")
                .contains("built_in_tools")
                .contains("end_call")
                .doesNotContain("secret");
        @SuppressWarnings("unchecked")
        var conversationConfig = (Map<String, Object>) agentBody.getValue().get("conversation_config");
        @SuppressWarnings("unchecked")
        var agent = (Map<String, Object>) conversationConfig.get("agent");
        @SuppressWarnings("unchecked")
        var prompt = (Map<String, Object>) agent.get("prompt");
        @SuppressWarnings("unchecked")
        var builtInTools = (Map<String, Object>) prompt.get("built_in_tools");
        @SuppressWarnings("unchecked")
        var endCall = (Map<String, Object>) builtInTools.get("end_call");
        assertThat(endCall)
                .containsEntry("type", "system")
                .containsEntry("name", "end_call");
        assertThat(endCall.get("params"))
                .isEqualTo(Map.of("system_tool_type", "end_call"));
    }

    @Test
    void telnyxCreatesAWebEnabledAssistantWithClientSideTools() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        when(http.post(eq("Telnyx"), eq(URI.create("https://api.telnyx.com/v2/ai/assistants")), any(), any()))
                .thenReturn(objectMapper.readTree("{\"id\":\"assistant-1\",\"version_id\":\"main\"}"));
        var provisioner = new TelnyxManagedVoiceAgentProvisioner(
                http,
                "secret",
                "https://api.telnyx.com/v2/"
        );

        var reference = provisioner.synchronize(blueprint(), null);

        assertThat(reference.externalAgentId()).isEqualTo("assistant-1");
        @SuppressWarnings("unchecked")
        var body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(http).post(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/assistants")),
                any(),
                body.capture()
        );
        assertThat(body.getValue().toString())
                .contains("client_side_tool")
                .contains("hangup")
                .doesNotContain("end_call")
                .doesNotContain("timeout_ms")
                .doesNotContain("promote_to_main")
                .contains("supports_unauthenticated_web_calls")
                .contains("Hello from Sauti")
                .doesNotContain("secret");
        @SuppressWarnings("unchecked")
        var telephonySettings = (Map<String, Object>) body.getValue().get("telephony_settings");
        assertThat(telephonySettings.get("recording_settings"))
                .isEqualTo(Map.of("enabled", false));
        assertThat(body.getValue().get("privacy_settings"))
                .isEqualTo(Map.of("data_retention", false));
    }

    @Test
    void telnyxPromotesAnUpdatedAssistantVersionToMain() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        when(http.post(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/assistants/assistant-1")),
                any(),
                any()
        )).thenReturn(objectMapper.readTree("{\"id\":\"assistant-1\",\"version_id\":\"version-2\"}"));
        var provisioner = new TelnyxManagedVoiceAgentProvisioner(
                http,
                "secret",
                "https://api.telnyx.com/v2/"
        );

        var reference = provisioner.synchronize(
                blueprint(),
                new ManagedVoiceAgentReference("assistant-1", "main", "{}")
        );

        assertThat(reference.externalVersionId()).isEqualTo("version-2");
        @SuppressWarnings("unchecked")
        var body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(http).post(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/assistants/assistant-1")),
                any(),
                body.capture()
        );
        assertThat(body.getValue())
                .containsEntry("promote_to_main", true);
        assertThat(body.getValue().toString())
                .doesNotContain("timeout_ms");
    }

    private ManagedVoiceAgentBlueprint blueprint() {
        return new ManagedVoiceAgentBlueprint(
                "Sauti Test",
                "Hello from Sauti",
                "Be concise and professional.",
                "en",
                List.of("en"),
                List.of(
                        new LlmToolDefinition(
                                "check_availability",
                                "Check availability.",
                                Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "date", Map.of(
                                                        "type", "string",
                                                        "description", "Appointment date.",
                                                        "format", "date",
                                                        "maxLength", 10
                                                ),
                                                "clear_fields", Map.of(
                                                        "type", "array",
                                                        "description", "Fields to clear.",
                                                        "items", Map.of("type", "string")
                                                ),
                                                "customer_details", Map.of(
                                                        "type", "object",
                                                        "properties", Map.of(),
                                                        "additionalProperties", true
                                                )
                                        ),
                                        "additionalProperties", false
                                ),
                                true
                        ),
                        new LlmToolDefinition(
                                "end_call",
                                "End the call.",
                                Map.of("type", "object"),
                                false
                        )
                ),
                300,
                0.7,
                300,
                List.of("Sauti")
        );
    }
}
