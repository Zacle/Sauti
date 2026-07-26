package com.sauti.call;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class TelnyxAiConversationService {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final String apiKey;
    private final String apiBaseUrl;

    public TelnyxAiConversationService(
            ManagedVoiceProviderHttpClient httpClient,
            @Value("${sauti.telnyx.api-key:}") String apiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String apiBaseUrl
    ) {
        this.httpClient = httpClient;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    public String create(Call call) {
        var response = httpClient.post(
                "Telnyx",
                URI.create(apiBaseUrl + "/ai/conversations"),
                headers(),
                Map.of(
                        "name", "Sauti browser voice call",
                        "metadata", Map.of(
                                "sauti_call_id", call.getId().toString(),
                                "sauti_call_sid", call.getTwilioCallSid()
                        )
                )
        );
        var id = response.path("data").path("id").asText(response.path("id").asText("")).trim();
        return validatedConversationId(id);
    }

    public String callControlId(String conversationId) {
        var id = validatedConversationId(conversationId);
        var response = httpClient.get(
                "Telnyx",
                URI.create(apiBaseUrl + "/ai/conversations/" + encode(id)),
                headers()
        );
        var data = response.has("data") ? response.path("data") : response;
        var callControlId = data.path("metadata").path("call_control_id").asText("").trim();
        if (callControlId.isBlank()) return "";
        if (!callControlId.startsWith("v3:") || callControlId.length() > 75) {
            throw new IllegalStateException("Telnyx returned an invalid call control id");
        }
        return callControlId;
    }

    public String callControlIdForSautiCall(Call call) {
        var createdAfter = call.getStartedAt().minusMinutes(1).toInstant().toString();
        var response = httpClient.get(
                "Telnyx",
                URI.create(apiBaseUrl + "/ai/conversations?created_at="
                        + encode("gte." + createdAfter) + "&order=created_at.asc&limit=100"),
                headers()
        );
        var callSidMarker = java.util.regex.Pattern.compile(
                "callSid=" + java.util.regex.Pattern.quote(call.getTwilioCallSid()) + "(?:\\b|$)"
        );
        String matched = "";
        for (var conversation : response.path("data")) {
            if (!callSidMarker.matcher(conversation.path("system_prompt").asText("")).find()) continue;
            var candidate = conversation.path("metadata").path("call_control_id").asText("").trim();
            if (candidate.isBlank()) continue;
            if (!candidate.startsWith("v3:") || candidate.length() > 75) {
                throw new IllegalStateException("Telnyx returned an invalid call control id");
            }
            if (!matched.isBlank() && !matched.equals(candidate)) {
                throw new IllegalStateException("Telnyx returned multiple conversations for one Sauti call");
            }
            matched = candidate;
        }
        return matched;
    }

    private Map<String, String> headers() {
        if (apiKey.isBlank()) throw new VoiceRuntimeUnavailableException("Telnyx API credentials are unavailable.");
        return Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
    }

    private static String validatedConversationId(String value) {
        var normalized = trim(value);
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Telnyx did not return a valid conversation id", exception);
        }
    }

    private static String encode(String value) {
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
