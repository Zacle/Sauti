package com.sauti.call;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class RetellBrowserVoiceRuntimeService implements BrowserVoiceRuntimeProvider {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final ManagedVoiceRuntimeSupport support;
    private final ManagedVoiceAgentProvisioningService provisioningService;
    private final String apiKey;
    private final String apiBaseUrl;

    public RetellBrowserVoiceRuntimeService(
            ManagedVoiceProviderHttpClient httpClient,
            ManagedVoiceRuntimeSupport support,
            ManagedVoiceAgentProvisioningService provisioningService,
            @Value("${sauti.retell.api-key:}") String apiKey,
            @Value("${sauti.retell.api-base-url:https://api.retellai.com}") String apiBaseUrl
    ) {
        this.httpClient = httpClient;
        this.support = support;
        this.provisioningService = provisioningService;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    @Override
    public String provider() {
        return "retell";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && provisioningService.isConfigured(provider());
    }

    @Override
    public BrowserVoiceRuntimeSession prepare(Call call, String greeting, String callbackToken) {
        if (!isConfigured()) {
            throw new IllegalStateException("Retell browser calls require RETELL_API_KEY.");
        }
        var managedAgent = provisioningService.resolve(provider(), call, greeting);
        var dynamicVariables = new LinkedHashMap<String, Object>();
        dynamicVariables.put("sauti_call_id", call.getId().toString());
        dynamicVariables.put("sauti_call_sid", call.getTwilioCallSid());
        dynamicVariables.put("sauti_tool_url", support.toolUrl(provider(), call, callbackToken));

        var body = new LinkedHashMap<String, Object>();
        body.put("agent_id", managedAgent.externalAgentId());
        body.put("metadata", Map.of(
                "sauti_call_id", call.getId().toString(),
                "sauti_agent_id", call.getAgent().getId().toString(),
                "test_call", true
        ));
        body.put("retell_llm_dynamic_variables", dynamicVariables);

        var response = httpClient.post(
                "Retell",
                URI.create(apiBaseUrl + "/v2/create-web-call"),
                Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey),
                body
        );
        var accessToken = requiredText(response.path("access_token"), "Retell did not return a web-call access token");
        var providerCallId = requiredText(response.path("call_id"), "Retell did not return a call id");
        return new BrowserVoiceRuntimeSession(
                provider(),
                accessToken,
                "",
                Map.of(
                        "providerCallId", providerCallId,
                        "greeting", greeting == null ? "" : greeting
                )
        );
    }

    private static String requiredText(com.fasterxml.jackson.databind.JsonNode value, String message) {
        var text = value.isTextual() ? value.asText().trim() : "";
        if (text.isBlank()) throw new IllegalStateException(message);
        return text;
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
