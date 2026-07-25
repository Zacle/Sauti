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
                .contains("name=hang_up")
                .contains("https://sauti.example/webhooks/telnyx/tools/check_availability")
                .contains("callSid={{sauti_call_sid}}")
                .contains("async=false")
                .contains("timeout_ms=30000")
                .contains("end_call")
                .doesNotContain("client_side_tool")
                .doesNotContain("promote_to_main");
        @SuppressWarnings("unchecked")
        var telephony = (Map<String, Object>) body.getValue().get("telephony_settings");
        assertThat(telephony.get("recording_settings")).isEqualTo(Map.of("enabled", false));
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
                "Telnyx.NaturalHD.astra"
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
