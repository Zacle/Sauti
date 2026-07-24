package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManagedBrowserVoiceRuntimeServicesTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retellMintsAnEphemeralAccessTokenAndKeepsTheApiKeyServerSide() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var support = mock(ManagedVoiceRuntimeSupport.class);
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
        var fixture = fixture();
        when(provisioning.isConfigured("retell")).thenReturn(true);
        when(provisioning.resolve("retell", fixture.call(), "Hello"))
                .thenReturn(new ManagedVoiceAgentReference("retell-agent", "1", "{}"));
        when(support.toolUrl("retell", fixture.call(), "call-token"))
                .thenReturn("https://sauti.uk/api/v1/public/managed-voice/retell/call-42/tool?token=call-token");
        when(http.post(eq("Retell"), any(URI.class), any(), any())).thenReturn(
                objectMapper.readTree("""
                        {"access_token":"retell-ephemeral","call_id":"retell-call-1"}
                        """)
        );
        var service = new RetellBrowserVoiceRuntimeService(
                http, support, provisioning, "retell-secret", "https://api.retellai.com/"
        );

        var session = service.prepare(fixture.call(), "Hello", "call-token");

        assertThat(session.provider()).isEqualTo("retell");
        assertThat(session.clientToken()).isEqualTo("retell-ephemeral");
        assertThat(session.configuration())
                .containsEntry("providerCallId", "retell-call-1")
                .doesNotContainValue("retell-secret")
                .doesNotContainValue("call-token");
        @SuppressWarnings("unchecked")
        var body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(http).post(eq("Retell"), eq(URI.create("https://api.retellai.com/v2/create-web-call")), any(), body.capture());
        assertThat(body.getValue().toString())
                .contains("sauti_tool_url")
                .doesNotContain("retell-secret");
    }

    @Test
    void elevenLabsMintsAWebRtcTokenAndPublishesOnlyEnabledClientToolNames() throws Exception {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var support = mock(ManagedVoiceRuntimeSupport.class);
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
        var fixture = fixture();
        when(provisioning.isConfigured("elevenlabs")).thenReturn(true);
        when(provisioning.resolve("elevenlabs", fixture.call(), "Hello"))
                .thenReturn(new ManagedVoiceAgentReference("agent_external", "2", "{}"));
        when(support.toolNames(fixture.call())).thenReturn(List.of("check_availability", "book_slot"));
        when(http.get(eq("ElevenLabs"), any(URI.class), any())).thenReturn(
                objectMapper.readTree("{\"token\":\"eleven-ephemeral\"}")
        );
        var service = new ElevenLabsBrowserVoiceRuntimeService(
                http, support, provisioning, "eleven-secret", "staging", "https://api.elevenlabs.io/"
        );

        var session = service.prepare(fixture.call(), "Hello", "call-token");

        assertThat(session.provider()).isEqualTo("elevenlabs");
        assertThat(session.clientToken()).isEqualTo("eleven-ephemeral");
        assertThat(session.configuration().get("toolNames"))
                .isEqualTo(List.of("check_availability", "book_slot"));
        assertThat(session.configuration().toString())
                .doesNotContain("eleven-secret")
                .doesNotContain("call-token");
        verify(http).get(
                eq("ElevenLabs"),
                eq(URI.create(
                        "https://api.elevenlabs.io/v1/convai/conversation/token"
                                + "?agent_id=agent_external&environment=staging"
                )),
                eq(Map.of("xi-api-key", "eleven-secret"))
        );
    }

    @Test
    void telnyxExposesOnlyPublicAssistantConfiguration() {
        var support = mock(ManagedVoiceRuntimeSupport.class);
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
        var fixture = fixture();
        when(provisioning.isConfigured("telnyx")).thenReturn(true);
        when(provisioning.resolve("telnyx", fixture.call(), "Hello"))
                .thenReturn(new ManagedVoiceAgentReference("assistant-42", "main", "{}"));
        when(support.toolNamesIncludingEndCall(fixture.call()))
                .thenReturn(List.of("check_availability", "end_call"));
        var service = new TelnyxAiBrowserVoiceRuntimeService(
                support, provisioning, "development", "eu-west"
        );

        var session = service.prepare(fixture.call(), "Hello", "call-token");

        assertThat(session.provider()).isEqualTo("telnyx");
        assertThat(session.clientToken()).isEmpty();
        assertThat(session.configuration())
                .containsEntry("agentId", "assistant-42")
                .containsEntry("environment", "development")
                .containsEntry("region", "eu-west")
                .containsEntry("toolNames", List.of("check_availability", "end_call"))
                .doesNotContainValue("call-token");
    }

    @Test
    void providersAreUnavailableUntilEveryRequiredCredentialIsPresent() {
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var support = mock(ManagedVoiceRuntimeSupport.class);
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);

        assertThat(new RetellBrowserVoiceRuntimeService(http, support, provisioning, "", "https://example.test")
                .isConfigured()).isFalse();
        assertThat(new ElevenLabsBrowserVoiceRuntimeService(
                http, support, provisioning, "key", "production", "https://example.test"
        ).isConfigured()).isFalse();
        assertThat(new TelnyxAiBrowserVoiceRuntimeService(support, provisioning, "production", "")
                .isConfigured()).isFalse();
        assertThatThrownBy(() -> new TelnyxAiBrowserVoiceRuntimeService(
                support, provisioning, "production", ""
        ).prepare(fixture().call(), "Hello", "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELNYX_API_KEY");
    }

    private Fixture fixture() {
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getId()).thenReturn(UUID.fromString("6f482e0e-6785-44b5-a544-eabf1c9fdf8a"));
        when(call.getTwilioCallSid()).thenReturn("call-42");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(UUID.fromString("48db9149-e363-4087-a814-754f1a9d61ef"));
        return new Fixture(call, agent);
    }

    private record Fixture(Call call, Agent agent) {
    }
}
