package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.call.BrowserVoiceRuntimeSession;
import com.sauti.call.TelnyxAiBrowserVoiceRuntimeService;
import com.sauti.call.VoiceRuntimeUnavailableException;
import com.sauti.call.WebVoiceTokenService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicDemoVoiceServiceTest {
    private final TelnyxAiBrowserVoiceRuntimeService runtime = mock(TelnyxAiBrowserVoiceRuntimeService.class);
    private final WebVoiceTokenService tokens = mock(WebVoiceTokenService.class);
    private final PublicDemoVoiceQuotaService quotas = mock(PublicDemoVoiceQuotaService.class);

    @Test
    void returnsOnlyTheDedicatedExternalAssistantConfiguration() {
        var expected = new BrowserVoiceRuntimeSession(
                "telnyx", "", "", Map.of("agentId", "demo-agent", "maxCallDurationSeconds", 60)
        );
        when(runtime.isConfigured()).thenReturn(true);
        when(runtime.prepareExternalAgent("demo-agent", "demo-version", 60)).thenReturn(expected);
        var service = service(true);

        var configuration = service.configuration("https://sauti.uk");

        assertThat(configuration.name()).isEqualTo("Sauti");
        assertThat(configuration.maxDurationSeconds()).isEqualTo(60);
        assertThat(configuration.runtime()).isEqualTo(expected);
        verify(quotas, never()).reserve(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reservesQuotaBeforeIssuingAOneMinuteSession() {
        var expected = new BrowserVoiceRuntimeSession("telnyx", "", "", Map.of("agentId", "demo-agent"));
        when(runtime.isConfigured()).thenReturn(true);
        when(runtime.prepareExternalAgent("demo-agent", "demo-version", 60)).thenReturn(expected);
        when(tokens.issue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(PublicDemoVoiceService.PUBLIC_AGENT_ID),
                org.mockito.ArgumentMatchers.eq(180L))).thenReturn("signed-token");
        var service = service(true);

        var session = service.start(
                "https://sauti.uk", "203.0.113.5", "1234567890abcdef", true
        );

        assertThat(session.sessionId()).startsWith("demo-");
        assertThat(session.token()).isEqualTo("signed-token");
        assertThat(session.maxDurationSeconds()).isEqualTo(60);
        verify(quotas).reserve(session.sessionId(), "203.0.113.5", "1234567890abcdef");
    }

    @Test
    void failsClosedWhenTheDedicatedAssistantIsDisabled() {
        var service = service(false);

        assertThatThrownBy(() -> service.configuration("https://sauti.uk"))
                .isInstanceOf(VoiceRuntimeUnavailableException.class);
        verify(runtime, never()).prepareExternalAgent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rejectsAnotherWebsiteBeforeConsumingQuota() {
        when(runtime.isConfigured()).thenReturn(true);
        var service = service(true);

        assertThatThrownBy(() -> service.start(
                "https://attacker.example", "203.0.113.5", "1234567890abcdef", true
        )).isInstanceOf(IllegalArgumentException.class);
        verify(quotas, never()).reserve(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private PublicDemoVoiceService service(boolean enabled) {
        return new PublicDemoVoiceService(
                runtime, tokens, quotas, enabled, "demo-agent", "demo-version", 60, "https://sauti.uk"
        );
    }
}
