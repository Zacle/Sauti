package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class TelnyxAiConversationServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsACallScopedConversationAndResolvesItsCallControlId() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var call = mock(Call.class);
        var callId = UUID.fromString("75343c7a-bb83-4bfe-a6ba-c08698db26d3");
        var conversationId = "236da7b5-0738-4977-8cd1-9c72db86eda5";
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("TEST-call-reference");
        when(http.post(any(), any(), any(), any()))
                .thenReturn(objectMapper.readTree("{\"id\":\"" + conversationId + "\"}"));
        when(http.get(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"data":{"metadata":{"call_control_id":"v3:provider-call"}}}
                """));
        var service = new TelnyxAiConversationService(
                http, "secret", "https://api.telnyx.com/v2/"
        );

        assertThat(service.create(call)).isEqualTo(conversationId);
        assertThat(service.callControlId(conversationId)).isEqualTo("v3:provider-call");

        verify(http).post(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/conversations")),
                eq(Map.of(HttpHeaders.AUTHORIZATION, "Bearer secret")),
                eq(Map.of(
                        "name", "Sauti browser voice call",
                        "metadata", Map.of(
                                "sauti_call_id", callId.toString(),
                                "sauti_call_sid", "TEST-call-reference"
                        )
                ))
        );
        verify(http).get(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/conversations/" + conversationId)),
                eq(Map.of(HttpHeaders.AUTHORIZATION, "Bearer secret"))
        );
    }

    @Test
    void recoversAnExistingCallOnlyThroughItsExactSautiCallReference() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var call = mock(Call.class);
        var startedAt = OffsetDateTime.parse("2026-07-26T13:26:04Z");
        when(call.getStartedAt()).thenReturn(startedAt);
        when(call.getTwilioCallSid()).thenReturn("TEST-exact-call");
        when(http.get(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"data":[
                  {
                    "system_prompt":"tool?callSid=TEST-other-call",
                    "metadata":{"call_control_id":"v3:wrong-call"}
                  },
                  {
                    "system_prompt":"tool?callSid=TEST-exact-call&tool=booking",
                    "metadata":{"call_control_id":"v3:exact-call"}
                  }
                ]}
                """));
        var service = new TelnyxAiConversationService(
                http, "secret", "https://api.telnyx.com/v2"
        );

        assertThat(service.callControlIdForSautiCall(call)).isEqualTo("v3:exact-call");

        verify(http).get(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/ai/conversations"
                        + "?created_at=gte.2026-07-26T13%3A25%3A04Z"
                        + "&order=created_at.asc&limit=100")),
                eq(Map.of(HttpHeaders.AUTHORIZATION, "Bearer secret"))
        );
    }
}
