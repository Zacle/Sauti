package com.sauti.call;

import java.util.List;
import java.util.UUID;

public final class WebVoiceDtos {
    private WebVoiceDtos() {
    }

    public record PublicAgentResponse(
            String publicId,
            String name,
            String description,
            String defaultLanguage,
            List<String> languages,
            boolean consentRequired,
            boolean recordingEnabled
    ) {
    }

    public record StartWebVoiceSessionRequest(
            boolean consentAccepted,
            String origin,
            String preferredLanguage
    ) {
    }

    public record StartWebVoiceSessionResponse(
            UUID callId,
            String sessionId,
            String token,
            String websocketUrl,
            String greeting,
            int inputSampleRate,
            String language
    ) {
    }
}
