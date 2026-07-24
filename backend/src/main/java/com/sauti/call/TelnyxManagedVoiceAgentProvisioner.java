package com.sauti.call;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class TelnyxManagedVoiceAgentProvisioner implements ManagedVoiceAgentProvisioner {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final String apiKey;
    private final String apiBaseUrl;

    public TelnyxManagedVoiceAgentProvisioner(
            ManagedVoiceProviderHttpClient httpClient,
            @Value("${sauti.telnyx.api-key:}") String apiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String apiBaseUrl
    ) {
        this.httpClient = httpClient;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    @Override
    public String provider() {
        return "telnyx";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String configurationVersion() {
        return "7";
    }

    @Override
    public ManagedVoiceAgentReference synchronize(
            ManagedVoiceAgentBlueprint blueprint,
        ManagedVoiceAgentReference existing
    ) {
        var headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        var updating = existing != null && !existing.externalAgentId().isBlank();
        var body = assistantBody(blueprint, updating);
        com.fasterxml.jackson.databind.JsonNode response;
        if (!updating) {
            response = httpClient.post(
                    "Telnyx",
                    URI.create(apiBaseUrl + "/ai/assistants"),
                    headers,
                    body
            );
        } else {
            response = httpClient.post(
                    "Telnyx",
                    URI.create(apiBaseUrl + "/ai/assistants/" + path(existing.externalAgentId())),
                    headers,
                    body
            );
        }
        var agentId = response.path("id").asText(existing == null ? "" : existing.externalAgentId()).trim();
        if (agentId.isBlank()) throw new IllegalStateException("Telnyx did not return an assistant id");
        var versionId = response.path("version_id").asText(
                existing == null ? "main" : existing.externalVersionId()
        );
        return new ManagedVoiceAgentReference(agentId, versionId, "{}");
    }

    private Map<String, Object> assistantBody(
            ManagedVoiceAgentBlueprint blueprint,
            boolean updating
    ) {
        var tools = new ArrayList<Map<String, Object>>();
        blueprint.tools().forEach(tool -> {
            var clientTool = new LinkedHashMap<String, Object>();
            clientTool.put("name", tool.name());
            var description = tool.description() == null ? "" : tool.description();
            if ("end_call".equals(tool.name())) {
                description = "Required terminal action. Call this immediately after one brief respectful farewell "
                        + "when the caller is finished or says goodbye. Do not wait for another caller turn.";
            } else if (tool.callerWaitExpected()) {
                description += " This operation may take noticeable time. Immediately before invoking it, say one "
                        + "brief, natural, professional progress acknowledgment in the caller's current language. "
                        + "Do not ask a question and do not imply success or failure. After the result returns, "
                        + "continue automatically and explain only the factual outcome.";
            }
            clientTool.put("description", description.trim());
            clientTool.put("parameters", tool.inputSchema());
            tools.add(Map.of(
                    "type", "client_side_tool",
                    "client_side_tool", Map.copyOf(clientTool)
            ));
        });
        var body = new LinkedHashMap<String, Object>();
        body.put("name", shorten(blueprint.name(), 100));
        body.put("instructions", blueprint.instructions() + """

                TELNYX EXECUTION CONTRACT:
                - Call a required business tool before speaking about its result.
                - Treat the returned result as authoritative. For any mutation, claim success only when the result
                  explicitly contains actionPerformed=true.
                - success=false or actionPerformed=false means nothing changed. Follow the returned instruction and
                  never describe the requested mutation as completed.
                - For an explicitly confirmed retained action, invoke the exact same tool and material arguments with
                  confirmation_state=confirmed and question_handling=ready_for_action. Do not ask repeatedly.
                - For a tool marked as potentially slow, speak its brief progress acknowledgment immediately before
                  invoking it, without asking the caller a question.
                - After every tool result, continue automatically in the same turn; never wait for more caller speech.
                - Keep each spoken answer continuous and concise.
                - When the caller is finished, say one brief respectful farewell and call end_call in the same turn.
                - Never finish a farewell without calling end_call. Never wait for another caller turn after the farewell.
                """);
        body.put("greeting", blueprint.greeting());
        body.put("tools", tools);
        body.put("enabled_features", java.util.List.of("telephony"));
        body.put("telephony_settings", Map.of(
                "supports_unauthenticated_web_calls", true,
                "time_limit_secs", Math.max(10, blueprint.maxCallDurationSeconds()),
                "user_idle_timeout_secs", 60,
                "recording_settings", Map.of("enabled", false)
        ));
        body.put("privacy_settings", Map.of("data_retention", false));
        body.put("interruption_settings", Map.of(
                "enable", true,
                "disable_greeting_interruption", false,
                "start_speaking_plan", Map.of(
                        "wait_seconds", 0.1,
                        "transcription_endpointing_plan", Map.of(
                                "on_punctuation_seconds", 0.1,
                                "on_no_punctuation_seconds", Math.max(
                                        0.3,
                                        Math.min(2.0, blueprint.endpointingMilliseconds() / 1000.0)
                                ),
                                "on_number_seconds", 0.6
                        )
                )
        ));
        if (updating) body.put("promote_to_main", true);
        body.put("tags", java.util.List.of("sauti-managed"));
        return Map.copyOf(body);
    }

    private static String path(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String shorten(String value, int maximum) {
        var normalized = trim(value);
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
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
