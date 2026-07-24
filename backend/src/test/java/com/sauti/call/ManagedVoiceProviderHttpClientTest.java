package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedVoiceProviderHttpClientTest {

    @Test
    void exposesOnlyStructuredValidationLocationsAndMessagesForAProvider422() throws Exception {
        var httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(422);
        when(response.body()).thenReturn("""
                {
                  "detail": [{
                    "loc": ["body", "conversation_config", "agent", "prompt", "tool_ids"],
                    "msg": "System tools are not supported in tool_ids",
                    "input": {"api_key": "must-never-appear"}
                  }]
                }
                """);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ManagedVoiceProviderHttpClient(httpClient, new ObjectMapper());

        assertThatThrownBy(() -> client.get(
                "ElevenLabs",
                URI.create("https://api.elevenlabs.io/v1/convai/tools"),
                Map.of("xi-api-key", "secret")
        ))
                .isInstanceOf(ManagedVoiceProviderException.class)
                .hasMessageContaining(
                        "body.conversation_config.agent.prompt.tool_ids: "
                                + "System tools are not supported in tool_ids"
                )
                .hasMessageNotContaining("must-never-appear")
                .hasMessageNotContaining("secret");
    }
}
