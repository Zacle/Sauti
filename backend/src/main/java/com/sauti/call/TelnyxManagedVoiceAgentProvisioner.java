package com.sauti.call;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class TelnyxManagedVoiceAgentProvisioner {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final String apiKey;
    private final String apiBaseUrl;
    private final String publicBaseUrl;
    private final String toolWebhookSecret;
    private final String defaultVoiceId;

    public TelnyxManagedVoiceAgentProvisioner(
            ManagedVoiceProviderHttpClient httpClient,
            @Value("${sauti.telnyx.api-key:}") String apiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String apiBaseUrl,
            @Value("${sauti.telnyx.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${sauti.telnyx.tool-webhook-secret:}") String toolWebhookSecret,
            @Value("${sauti.telnyx.default-voice-id:Telnyx.NaturalHD.astra}") String defaultVoiceId
    ) {
        this.httpClient = httpClient;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.toolWebhookSecret = trim(toolWebhookSecret);
        this.defaultVoiceId = trim(defaultVoiceId);
    }

    public String provider() {
        return "telnyx";
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !publicBaseUrl.isBlank() && !toolWebhookSecret.isBlank();
    }

    public String configurationVersion() {
        return "16";
    }

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
            // Telnyx must have exactly one terminal tool. Map Sauti's portable
            // end_call name to the provider-native hangup below rather than
            // requiring the model to execute a webhook and a second tool.
            if ("end_call".equals(tool.name())) return;
            var webhook = new LinkedHashMap<String, Object>();
            webhook.put("name", tool.name());
            var description = tool.description() == null ? "" : tool.description();
            if (tool.callerWaitExpected()) {
                description += " This operation may take noticeable time. Immediately before invoking it, say one "
                        + "brief, natural, professional progress acknowledgment in the caller's current language. "
                        + "Do not ask a question and do not imply success or failure. After the result returns, "
                        + "continue automatically and explain only the factual outcome.";
            }
            webhook.put("description", description.trim());
            webhook.put(
                    "url",
                    publicBaseUrl + "/webhooks/telnyx/tools/" + path(tool.name())
                            + "?callSid={{sauti_call_sid}}"
            );
            webhook.put("method", "POST");
            // Sauti keeps factual CRUD results synchronous so Telnyx resumes the
            // same turn with the authoritative response. Telnyx otherwise uses
            // a roughly five-second default, which is too short for a guarded
            // calendar or integration operation.
            webhook.put("async", false);
            webhook.put("timeout_ms", 30_000);
            webhook.put("headers", java.util.List.of(
                    Map.of("name", "x-sauti-tool-secret", "value", toolWebhookSecret)
            ));
            webhook.put("body_parameters", tool.inputSchema());
            tools.add(Map.of(
                    "type", "webhook",
                    "webhook", Map.copyOf(webhook)
            ));
        });
        tools.add(Map.of(
                "type", "hangup",
                "hangup", Map.of(
                        "description", "For phone_call conversations only: after one brief respectful farewell, "
                                + "immediately end the call when the caller clearly indicates they are finished."
                )
        ));
        tools.add(Map.of(
                "type", "client_side_tool",
                "client_side_tool", Map.of(
                        "name", "end_browser_call",
                        "description", "For web_call conversations only: after one brief respectful farewell, "
                                + "immediately end the browser voice conversation when the caller clearly indicates "
                                + "they are finished.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", java.util.List.of()
                        )
                )
        ));
        var body = new LinkedHashMap<String, Object>();
        body.put("name", shorten(blueprint.name(), 100));
        body.put("instructions", blueprint.instructions() + """

                TELNYX EXECUTION CONTRACT:
                - Call a required business tool before speaking about its result.
                - success=true means the tool request was processed. It does not by itself mean a business mutation
                  happened. Only actionPerformed=true means external data changed.
                - Treat the returned result as authoritative. For any mutation, claim success only when the result
                  explicitly contains actionPerformed=true.
                - workflowPending=true or actionPerformed=false is a valid workflow step, not a tool failure. Follow
                  instruction, nextTool, nextToolArguments, and nextToolAuthorized exactly. Do not retry the same
                  mutation merely because nothing changed yet.
                - success=false means the tool itself failed. Never describe the requested mutation as completed.
                - For an explicitly confirmed retained action, invoke the exact same tool and material arguments with
                  confirmation_state=confirmed and question_handling=ready_for_action. Do not ask repeatedly.
                - For a tool marked as potentially slow, speak its brief progress acknowledgment immediately before
                  invoking it, without asking the caller a question.
                - After every tool result, continue automatically in the same turn; never wait for more caller speech.
                - Keep each spoken answer continuous and concise.
                - On Telnyx, do not call the portable end_call webhook. Use Sauti's explicit conversation channel
                  `{{sauti_conversation_channel}}` to select exactly one terminal action. For `web_call`, say one
                  brief respectful farewell and immediately invoke `end_browser_call`. For `phone_call`, say one brief
                  respectful farewell and immediately invoke Telnyx's native `hangup`. Do not pass spoken_farewell,
                  outcome, or summary arguments, do not call a webhook first, and never wait for another caller turn
                  after the farewell.
                """);
        body.put("greeting", blueprint.greeting());
        // Telnyx currently reports AI Agent SDK WebRTC sessions as phone_call.
        // Keep phone calls as the safe default and let the browser override this
        // variable through X-Sauti-Conversation-Channel at conversation start.
        body.put("dynamic_variables", Map.of("sauti_conversation_channel", "phone_call"));
        body.put("voice_settings", Map.of("voice", selectedVoice(blueprint.voiceId())));
        var transcription = new LinkedHashMap<String, Object>();
        transcription.put("model", "deepgram/nova-3");
        transcription.put(
                "language",
                blueprint.supportedLanguages().size() > 1 ? "auto" : blueprint.language()
        );
        var transcriptionSettings = new LinkedHashMap<String, Object>();
        transcriptionSettings.put("smart_format", true);
        transcriptionSettings.put("numerals", true);
        var keyterms = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("SAT", "Sauti"),
                        blueprint.boostedKeywords().stream()
                )
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.replace(",", " "))
                .distinct()
                .limit(100)
                .toList();
        transcriptionSettings.put("keyterm", String.join(",", keyterms));
        transcription.put("settings", Map.copyOf(transcriptionSettings));
        body.put("transcription", Map.copyOf(transcription));
        body.put("tools", tools);
        body.put("enabled_features", java.util.List.of("telephony"));
        body.put("telephony_settings", Map.of(
                "supports_unauthenticated_web_calls", true,
                "time_limit_secs", Math.max(10, blueprint.maxCallDurationSeconds()),
                "user_idle_timeout_secs", 60,
                "recording_settings", Map.of(
                        "enabled", true,
                        "channels", "dual",
                        "format", "mp3",
                        "stop_on_conversation_end", true
                )
        ));
        // Telnyx validation 10015 requires provider data retention whenever
        // assistant-managed recording is enabled.
        body.put("privacy_settings", Map.of("data_retention", true));
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

    private String selectedVoice(String configuredVoice) {
        var normalized = trim(configuredVoice);
        return normalized.toLowerCase(java.util.Locale.ROOT).startsWith("telnyx.")
                ? normalized
                : defaultVoiceId;
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
