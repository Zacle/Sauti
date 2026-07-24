package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class RetellManagedVoiceAgentProvisioner implements ManagedVoiceAgentProvisioner {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiBaseUrl;
    private final String defaultVoiceId;
    private final String model;

    public RetellManagedVoiceAgentProvisioner(
            ManagedVoiceProviderHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${sauti.retell.api-key:}") String apiKey,
            @Value("${sauti.retell.api-base-url:https://api.retellai.com}") String apiBaseUrl,
            @Value("${sauti.retell.default-voice-id:retell-Cimo}") String defaultVoiceId,
            @Value("${sauti.retell.model:gpt-4.1-mini}") String model
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.defaultVoiceId = trim(defaultVoiceId).isBlank() ? "retell-Cimo" : trim(defaultVoiceId);
        this.model = trim(model).isBlank() ? "gpt-4.1-mini" : trim(model);
    }

    @Override
    public String provider() {
        return "retell";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String configurationVersion() {
        return "3";
    }

    @Override
    public ManagedVoiceAgentReference synchronize(
            ManagedVoiceAgentBlueprint blueprint,
            ManagedVoiceAgentReference existing
    ) {
        var headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        var resources = resources(existing);
        var llmId = text(resources.get("llmId"));
        if (llmId.isBlank()) {
            var llmResponse = httpClient.post(
                    "Retell",
                    URI.create(apiBaseUrl + "/create-retell-llm"),
                    headers,
                    llmBody(blueprint)
            );
            llmId = requiredText(llmResponse.path("llm_id"), "Retell did not return a response-engine id");
        } else {
            httpClient.patch(
                    "Retell",
                    URI.create(apiBaseUrl + "/update-retell-llm/" + path(llmId)),
                    headers,
                    llmBody(blueprint)
            );
        }

        String agentId;
        String version;
        if (existing == null || existing.externalAgentId().isBlank()) {
            var agentResponse = httpClient.post(
                    "Retell",
                    URI.create(apiBaseUrl + "/create-agent"),
                    headers,
                    agentBody(blueprint, llmId)
            );
            agentId = requiredText(agentResponse.path("agent_id"), "Retell did not return an agent id");
            version = agentResponse.path("version").asText("");
        } else {
            agentId = existing.externalAgentId();
            var agentResponse = httpClient.patch(
                    "Retell",
                    URI.create(apiBaseUrl + "/update-agent/" + path(agentId)),
                    headers,
                    agentBody(blueprint, llmId)
            );
            version = agentResponse.path("version").asText(existing.externalVersionId());
        }
        return new ManagedVoiceAgentReference(
                agentId,
                version,
                json(Map.of("llmId", llmId))
        );
    }

    private Map<String, Object> llmBody(ManagedVoiceAgentBlueprint blueprint) {
        var tools = new ArrayList<Map<String, Object>>();
        blueprint.tools().forEach(tool -> {
            if ("end_call".equals(tool.name())) {
                tools.add(Map.of(
                        "type", "end_call",
                        "name", "end_call",
                        "description", "End the call after one brief, respectful farewell."
                ));
                return;
            }
            var custom = new LinkedHashMap<String, Object>();
            custom.put("type", "custom");
            custom.put("name", tool.name());
            custom.put("description", tool.description() == null ? "" : tool.description());
            custom.put("url", "{{sauti_tool_url}}");
            custom.put("method", "POST");
            custom.put("parameters", tool.inputSchema());
            custom.put("speak_after_execution", true);
            if (tool.callerWaitExpected()) {
                custom.put("speak_during_execution", true);
                custom.put("execution_message_type", "prompt");
                custom.put(
                        "execution_message_description",
                        "Generate one brief, natural, professional progress update in the caller's current "
                                + "language. Say that you are still working on the requested operation. "
                                + "Do not ask a question and do not imply that it succeeded or failed. "
                                + "After this update, stop speaking and wait for the tool result."
                );
            } else {
                custom.put("speak_during_execution", false);
            }
            custom.put("timeout_ms", 30_000);
            tools.add(Map.copyOf(custom));
        });
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("model_temperature", 0.2);
        body.put("tool_call_strict_mode", true);
        body.put("start_speaker", "agent");
        body.put("begin_message", blueprint.greeting());
        body.put("general_prompt", blueprint.instructions() + """

                RETELL EXECUTION CONTRACT:
                - Call a required business tool before speaking about its result.
                - Treat the returned result as authoritative. For any mutation, claim success only when the result
                  explicitly contains actionPerformed=true.
                - success=false or actionPerformed=false means nothing changed. Follow the returned instruction and
                  never describe the requested mutation as completed.
                - For an explicitly confirmed retained action, invoke the exact same tool and material arguments with
                  confirmation_state=confirmed and question_handling=ready_for_action. Do not ask repeatedly.
                - If a tool is still running, acknowledge the wait naturally and continue automatically when it returns.
                - When the caller is finished, say one brief respectful farewell and use the native end_call tool.
                """);
        body.put("general_tools", tools);
        return Map.copyOf(body);
    }

    private Map<String, Object> agentBody(ManagedVoiceAgentBlueprint blueprint, String llmId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("response_engine", Map.of("type", "retell-llm", "llm_id", llmId));
        body.put("voice_id", defaultVoiceId);
        body.put("agent_name", shorten(blueprint.name(), 40));
        body.put("language", retellLanguage(blueprint.language()));
        body.put("responsiveness", 1);
        body.put("interruption_sensitivity", blueprint.interruptionSensitivity());
        body.put("max_call_duration_ms", Math.max(10, blueprint.maxCallDurationSeconds()) * 1000L);
        body.put("end_call_after_silence_ms", 60_000);
        body.put("data_storage_setting", "everything_except_pii");
        if (!blueprint.boostedKeywords().isEmpty()) {
            body.put("boosted_keywords", blueprint.boostedKeywords());
        }
        return Map.copyOf(body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resources(ManagedVoiceAgentReference existing) {
        if (existing == null || existing.externalResourcesJson().isBlank()) return Map.of();
        try {
            return objectMapper.readValue(existing.externalResourcesJson(), Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored Retell agent binding is invalid", exception);
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store Retell agent binding", exception);
        }
    }

    private static String retellLanguage(String language) {
        var normalized = trim(language).replace('_', '-');
        if (normalized.isBlank()) return "en-US";
        if (normalized.contains("-")) return normalized;
        return switch (normalized.toLowerCase(java.util.Locale.ROOT)) {
            case "en" -> "en-US";
            case "fr" -> "fr-FR";
            case "ar" -> "ar-SA";
            case "sw" -> "sw-KE";
            default -> normalized;
        };
    }

    private static String path(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        return value instanceof String text ? text.trim() : "";
    }

    private static String requiredText(com.fasterxml.jackson.databind.JsonNode value, String message) {
        var text = value.isTextual() ? value.asText().trim() : "";
        if (text.isBlank()) throw new IllegalStateException(message);
        return text;
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
