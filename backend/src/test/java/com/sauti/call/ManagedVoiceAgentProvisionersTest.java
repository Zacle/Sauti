package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void telnyxCreatesAWebAndTelephoneAssistantWithWebhookTools() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        when(http.post(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/assistants")),
                any(),
                any()
        )).thenReturn(objectMapper.readTree("{\"id\":\"assistant-1\",\"version_id\":\"main\"}"));
        var provisioner = provisioner(http);

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
        assertThat(body.getValue())
                .containsEntry("voice_settings", Map.of("voice", "Telnyx.NaturalHD.astra"))
                .containsEntry("transcription", Map.of(
                        "model", "deepgram/nova-3",
                        "language", "auto",
                        "settings", Map.of(
                                "smart_format", true,
                                "numerals", true,
                                "keyterm", "SAT,Sauti"
                        )
                ));
        assertThat(body.getValue().toString())
                .contains("type=webhook")
                .contains("type=hangup")
                .contains("type=client_side_tool")
                .contains("name=end_browser_call")
                .contains("https://sauti.example/webhooks/telnyx/tools/check_availability")
                .contains("callSid={{sauti_call_sid}}")
                .contains("async=false")
                .contains("timeout_ms=30000")
                .contains("end_call")
                .doesNotContain("/webhooks/telnyx/tools/end_call")
                .doesNotContain("name=hang_up", "name=end_call")
                .doesNotContain("promote_to_main");
        @SuppressWarnings("unchecked")
        var tools = (List<Map<String, Object>>) body.getValue().get("tools");
        assertThat(tools).filteredOn(tool -> "hangup".equals(tool.get("type")))
                .singleElement()
                .satisfies(tool -> assertThat(tool.get("hangup")).isEqualTo(Map.of(
                        "description", "For phone_call conversations only: when the caller clearly indicates they "
                                + "are finished, first speak one complete farewell of no more than six words. Invoke "
                                + "this tool only after the final word has finished playing. Never invoke it while "
                                + "farewell speech is still being generated or played."
                )));
        assertThat(tools).filteredOn(tool -> "client_side_tool".equals(tool.get("type")))
                .singleElement()
                .satisfies(tool -> assertThat(tool.get("client_side_tool")).isEqualTo(Map.of(
                        "name", "end_browser_call",
                        "description", "For web_call conversations only: when the caller clearly indicates they are "
                                + "finished, first speak one complete farewell of no more than six words. Invoke this "
                                + "tool only after the final word has finished playing. Never invoke it while farewell "
                                + "speech is still being generated or played. Speaking the farewell without invoking "
                                + "this tool does not end the conversation.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                )));
        assertThat(tools).filteredOn(tool -> "webhook".equals(tool.get("type")))
                .allSatisfy(tool -> assertThat(tool.toString()).doesNotContain("name=end_call"));
        assertThat(body.getValue().get("instructions").toString())
                .contains("do not call the portable end_call webhook")
                .contains("{{sauti_conversation_channel}}", "web_call", "end_browser_call", "phone_call")
                .contains(
                        "required semantic boundary whenever a caller turn supplies",
                        "Do not invoke it for a greeting",
                        "never acknowledge a value only in",
                        "name introduction without an",
                        "every digit in the caller's finished sequence is unambiguous",
                        "Never reconstruct uncertain sounds",
                        "reproduce each array element",
                        "exactly once and in its original order",
                        "Never regenerate the number from conversational memory",
                        "A caller correction is",
                        "correct_review"
                )
                .doesNotContain("{{telnyx_conversation_channel}}")
                .contains("native", "hangup")
                .contains(
                        "spoken_farewell, outcome",
                        "call a webhook first",
                        "spoken farewell alone",
                        "final word has completely finished",
                        "Never invoke a terminal tool in parallel"
                );
        assertThat(body.getValue().get("dynamic_variables"))
                .isEqualTo(Map.of("sauti_conversation_channel", "phone_call"));
        assertThat(body.getValue()).containsEntry("model", "anthropic/claude-haiku-4-5");
        @SuppressWarnings("unchecked")
        var telephony = (Map<String, Object>) body.getValue().get("telephony_settings");
        assertThat(telephony.get("recording_settings")).isEqualTo(Map.of(
                "enabled", true,
                "channels", "dual",
                "format", "mp3",
                "stop_on_conversation_end", true
        ));
        assertThat(body.getValue().get("privacy_settings"))
                .isEqualTo(Map.of("data_retention", true));
        assertThat(body.getValue().get("interruption_settings")).isEqualTo(Map.of(
                "enable", true,
                "disable_greeting_interruption", true,
                "start_speaking_plan", Map.of(
                        "wait_seconds", 0.15,
                        "transcription_endpointing_plan", Map.of(
                                "on_punctuation_seconds", 0.1,
                                "on_no_punctuation_seconds", 0.9,
                                "on_number_seconds", 1.0
                        )
                )
        ));
    }

    @Test
    void telnyxReplacesAnEnglishDefaultVoiceForFrenchSpeech() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        when(http.post(any(), any(), any(), any()))
                .thenReturn(objectMapper.readTree("{\"id\":\"assistant-1\",\"version_id\":\"main\"}"));
        var french = new ManagedVoiceAgentBlueprint(
                "Sauti Test",
                "Bonjour",
                "Répondez en français.",
                "Telnyx.NaturalHD.astra",
                "fr",
                List.of("fr"),
                List.of(),
                300,
                0.7,
                300,
                List.of("Sauti")
        );

        provisioner(http).synchronize(french, null);

        @SuppressWarnings("unchecked")
        var body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(http).post(any(), any(), any(), body.capture());
        assertThat(body.getValue())
                .containsEntry("voice_settings", Map.of("voice", "Telnyx.NaturalHD.amarante"));
    }

    @Test
    void telnyxPromotesAnUpdatedAssistantVersionToMain() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        when(http.post(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/assistants/assistant-1")),
                any(),
                any()
        )).thenReturn(objectMapper.readTree(
                "{\"id\":\"assistant-1\",\"version_id\":\"version-2\"}"
        ));

        var reference = provisioner(http).synchronize(
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
        assertThat(body.getValue()).containsEntry("promote_to_main", true);
    }

    private TelnyxManagedVoiceAgentProvisioner provisioner(ManagedVoiceProviderHttpClient http) {
        return new TelnyxManagedVoiceAgentProvisioner(
                http,
                "secret",
                "https://api.telnyx.com/v2/",
                "https://sauti.example",
                "tool-secret",
                "Telnyx.NaturalHD.astra",
                "anthropic/claude-haiku-4-5"
        );
    }

    private ManagedVoiceAgentBlueprint blueprint() {
        return new ManagedVoiceAgentBlueprint(
                "Sauti Test",
                "Hello from Sauti",
                "Be concise and professional.",
                "Telnyx.NaturalHD.astra",
                "en",
                List.of("en", "fr"),
                List.of(
                        new LlmToolDefinition(
                                "check_availability",
                                "Check availability.",
                                Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "date", Map.of("type", "string")
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
