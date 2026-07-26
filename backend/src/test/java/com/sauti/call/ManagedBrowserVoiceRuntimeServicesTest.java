package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManagedBrowserVoiceRuntimeServicesTest {
    @Test
    void telnyxExposesOnlyPublicAssistantConfiguration() {
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
        var conversations = mock(TelnyxAiConversationService.class);
        var calls = mock(CallRepository.class);
        var fixture = fixture();
        when(provisioning.isConfigured()).thenReturn(true);
        when(provisioning.resolve(fixture.call(), "Hello"))
                .thenReturn(new ManagedVoiceAgentReference("assistant-42", "main", "{}"));
        when(conversations.create(fixture.call()))
                .thenReturn("236da7b5-0738-4977-8cd1-9c72db86eda5");
        var service = new TelnyxAiBrowserVoiceRuntimeService(
                provisioning, conversations, calls, "development", "eu-west"
        );

        var session = service.prepare(fixture.call(), "Hello", "call-token");

        assertThat(session.provider()).isEqualTo("telnyx");
        assertThat(session.clientToken()).isEmpty();
        assertThat(session.configuration())
                .containsEntry("agentId", "assistant-42")
                .containsEntry("environment", "development")
                .containsEntry("region", "eu-west")
                .containsEntry("conversationId", "236da7b5-0738-4977-8cd1-9c72db86eda5")
                .doesNotContainValue("call-token");
    }

    @Test
    void telnyxIsUnavailableUntilItsServerConfigurationIsPresent() {
        var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
        var service = new TelnyxAiBrowserVoiceRuntimeService(
                provisioning,
                mock(TelnyxAiConversationService.class),
                mock(CallRepository.class),
                "production",
                ""
        );

        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.prepare(fixture().call(), "Hello", "token"))
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
