package com.sauti.demo;

import com.sauti.call.TelnyxAiBrowserVoiceRuntimeService;
import com.sauti.call.VoiceRuntimeUnavailableException;
import com.sauti.call.WebVoiceTokenService;
import com.sauti.demo.PublicDemoVoiceDtos.PublicDemoVoiceConfiguration;
import com.sauti.demo.PublicDemoVoiceDtos.StartPublicDemoVoiceResponse;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublicDemoVoiceService {
    public static final String PUBLIC_AGENT_ID = "sauti-public-demo";
    private final TelnyxAiBrowserVoiceRuntimeService runtime;
    private final WebVoiceTokenService tokens;
    private final PublicDemoVoiceQuotaService quotas;
    private final boolean enabled;
    private final String agentId;
    private final String versionId;
    private final int maxDurationSeconds;
    private final Set<String> allowedOrigins;

    public PublicDemoVoiceService(
            TelnyxAiBrowserVoiceRuntimeService runtime,
            WebVoiceTokenService tokens,
            PublicDemoVoiceQuotaService quotas,
            @Value("${sauti.public-demo.enabled:false}") boolean enabled,
            @Value("${sauti.public-demo.telnyx-agent-id:}") String agentId,
            @Value("${sauti.public-demo.telnyx-version-id:}") String versionId,
            @Value("${sauti.public-demo.max-duration-seconds:60}") int maxDurationSeconds,
            @Value("${sauti.public-demo.allowed-origins:${sauti.dashboard.base-url}}") String allowedOrigins
    ) {
        this.runtime = runtime;
        this.tokens = tokens;
        this.quotas = quotas;
        this.enabled = enabled;
        this.agentId = agentId == null ? "" : agentId.trim();
        this.versionId = versionId == null ? "" : versionId.trim();
        this.maxDurationSeconds = Math.max(15, Math.min(90, maxDurationSeconds));
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public PublicDemoVoiceConfiguration configuration(String origin) {
        requireAvailable(origin);
        return new PublicDemoVoiceConfiguration(
                "Sauti",
                "Ask how Sauti answers customers, supports languages, and connects business workflows.",
                "Hello, I’m Sauti. Ask me how I can help your business handle customer conversations.",
                maxDurationSeconds,
                runtime.prepareExternalAgent(agentId, versionId, maxDurationSeconds)
        );
    }

    public StartPublicDemoVoiceResponse start(String origin, String clientAddress, String deviceId, boolean consent) {
        requireAvailable(origin);
        if (!consent) throw new IllegalArgumentException("Microphone consent is required");
        var normalizedDevice = deviceId == null ? "" : deviceId.trim();
        if (!normalizedDevice.matches("[a-zA-Z0-9_-]{16,100}")) {
            throw new IllegalArgumentException("A valid demo device identifier is required");
        }
        var sessionId = "demo-" + UUID.randomUUID();
        quotas.reserve(sessionId, clientAddress, normalizedDevice);
        try {
            return new StartPublicDemoVoiceResponse(
                    sessionId,
                    tokens.issue(sessionId, PUBLIC_AGENT_ID, maxDurationSeconds + 120L),
                    maxDurationSeconds,
                    runtime.prepareExternalAgent(agentId, versionId, maxDurationSeconds)
            );
        } catch (RuntimeException exception) {
            quotas.release(sessionId);
            throw exception;
        }
    }

    public void complete(String sessionId, String token) {
        var principal = tokens.verify(token);
        if (!sessionId.equals(principal.callSid()) || !PUBLIC_AGENT_ID.equals(principal.publicAgentId())) {
            throw new IllegalArgumentException("Invalid public demo session token");
        }
        quotas.release(sessionId);
    }

    private void requireAvailable(String origin) {
        if (!enabled || agentId.isBlank() || !runtime.isConfigured()) {
            throw new VoiceRuntimeUnavailableException("The public voice demo is temporarily unavailable.");
        }
        var normalizedOrigin = origin == null ? "" : origin.trim().toLowerCase(Locale.ROOT);
        if (normalizedOrigin.isBlank() || !allowedOrigins.contains(normalizedOrigin)) {
            throw new IllegalArgumentException("This website is not allowed to start the public voice demo");
        }
    }
}
