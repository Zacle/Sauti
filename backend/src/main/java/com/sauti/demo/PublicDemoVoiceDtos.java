package com.sauti.demo;

import com.sauti.call.BrowserVoiceRuntimeSession;

public final class PublicDemoVoiceDtos {
    private PublicDemoVoiceDtos() { }

    public record PublicDemoVoiceConfiguration(
            String name,
            String description,
            String greeting,
            int maxDurationSeconds,
            BrowserVoiceRuntimeSession runtime
    ) { }

    public record StartPublicDemoVoiceRequest(String deviceId, boolean consentAccepted, String origin) { }

    public record StartPublicDemoVoiceResponse(
            String sessionId,
            String token,
            int maxDurationSeconds,
            BrowserVoiceRuntimeSession runtime
    ) { }
}
