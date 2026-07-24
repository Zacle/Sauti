package com.sauti.call;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ElevenLabsBrowserVoiceRuntimeService implements BrowserVoiceRuntimeProvider {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final ManagedVoiceRuntimeSupport support;
    private final ManagedVoiceAgentProvisioningService provisioningService;
    private final String apiKey;
    private final String environment;
    private final String apiBaseUrl;

    public ElevenLabsBrowserVoiceRuntimeService(
            ManagedVoiceProviderHttpClient httpClient,
            ManagedVoiceRuntimeSupport support,
            ManagedVoiceAgentProvisioningService provisioningService,
            @Value("${sauti.elevenlabs.api-key:}") String apiKey,
            @Value("${sauti.elevenlabs.environment:production}") String environment,
            @Value("${sauti.elevenlabs.api-base-url:https://api.elevenlabs.io}") String apiBaseUrl
    ) {
        this.httpClient = httpClient;
        this.support = support;
        this.provisioningService = provisioningService;
        this.apiKey = trim(apiKey);
        this.environment = trim(environment).isBlank() ? "production" : trim(environment);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    @Override
    public String provider() {
        return "elevenlabs";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && provisioningService.isConfigured(provider());
    }

    @Override
    public BrowserVoiceRuntimeSession prepare(Call call, String greeting, String callbackToken) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "ElevenLabs browser calls require ELEVENLABS_API_KEY."
            );
        }
        var managedAgent = provisioningService.resolve(provider(), call, greeting);
        var agentId = managedAgent.externalAgentId();
        var query = "?agent_id=" + url(agentId) + "&environment=" + url(environment);
        var response = httpClient.get(
                "ElevenLabs",
                URI.create(apiBaseUrl + "/v1/convai/conversation/token" + query),
                Map.of("xi-api-key", apiKey)
        );
        var token = response.path("token").isTextual() ? response.path("token").asText().trim() : "";
        if (token.isBlank()) throw new IllegalStateException("ElevenLabs did not return a conversation token");

        var dynamicVariables = new LinkedHashMap<String, Object>();
        dynamicVariables.put("sauti_call_id", call.getId().toString());
        dynamicVariables.put("sauti_call_sid", call.getTwilioCallSid());
        return new BrowserVoiceRuntimeSession(
                provider(),
                token,
                "",
                Map.of(
                        "agentId", agentId,
                        "environment", environment,
                        "greeting", greeting == null ? "" : greeting,
                        "dynamicVariables", Map.copyOf(dynamicVariables),
                        "toolNames", support.toolNames(call)
                )
        );
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        var normalized = trim(value);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
