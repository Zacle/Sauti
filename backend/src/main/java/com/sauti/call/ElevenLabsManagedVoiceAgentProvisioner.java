package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.LlmToolDefinition;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ElevenLabsManagedVoiceAgentProvisioner implements ManagedVoiceAgentProvisioner {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiBaseUrl;

    public ElevenLabsManagedVoiceAgentProvisioner(
            ManagedVoiceProviderHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${sauti.elevenlabs.api-key:}") String apiKey,
            @Value("${sauti.elevenlabs.api-base-url:https://api.elevenlabs.io}") String apiBaseUrl
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    @Override
    public String provider() {
        return "elevenlabs";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String configurationVersion() {
        return "4";
    }

    @Override
    public ManagedVoiceAgentReference synchronize(
            ManagedVoiceAgentBlueprint blueprint,
            ManagedVoiceAgentReference existing
    ) {
        var headers = Map.of("xi-api-key", apiKey);
        var existingToolIds = toolIds(existing);
        var synchronizedToolIds = new LinkedHashMap<String, String>();
        for (var tool : blueprint.tools()) {
            if (isBuiltInTool(tool)) continue;
            var body = toolBody(tool);
            var toolId = trim(existingToolIds.get(tool.name()));
            if (toolId.isBlank()) {
                var response = httpClient.post(
                        "ElevenLabs",
                        URI.create(apiBaseUrl + "/v1/convai/tools"),
                        headers,
                        body
                );
                toolId = requiredText(response.path("id"), "ElevenLabs did not return a tool id");
            } else {
                httpClient.patch(
                        "ElevenLabs",
                        URI.create(apiBaseUrl + "/v1/convai/tools/" + path(toolId)),
                        headers,
                        body
                );
            }
            synchronizedToolIds.put(tool.name(), toolId);
        }

        var body = agentBody(
                blueprint,
                new ArrayList<>(synchronizedToolIds.values()),
                blueprint.tools().stream().anyMatch(this::isBuiltInTool)
        );
        String agentId;
        String versionId;
        if (existing == null || existing.externalAgentId().isBlank()) {
            var response = httpClient.post(
                    "ElevenLabs",
                    URI.create(apiBaseUrl + "/v1/convai/agents/create"),
                    headers,
                    body
            );
            agentId = requiredText(response.path("agent_id"), "ElevenLabs did not return an agent id");
            versionId = response.path("version_id").asText("");
        } else {
            agentId = existing.externalAgentId();
            var response = httpClient.patch(
                    "ElevenLabs",
                    URI.create(apiBaseUrl + "/v1/convai/agents/" + path(agentId)),
                    headers,
                    body
            );
            versionId = response.path("version_id").asText(existing.externalVersionId());
        }
        return new ManagedVoiceAgentReference(
                agentId,
                versionId,
                json(Map.of("toolIds", synchronizedToolIds))
        );
    }

    private Map<String, Object> toolBody(LlmToolDefinition tool) {
        var client = new LinkedHashMap<String, Object>();
        client.put("type", "client");
        client.put("name", tool.name());
        client.put("description", tool.description() == null ? "" : tool.description());
        client.put("expects_response", true);
        client.put("response_timeout_secs", 30);
        client.put("parameters", elevenLabsSchema(tool.inputSchema()));
        return Map.of("tool_config", Map.copyOf(client));
    }

    private Map<String, Object> elevenLabsSchema(Map<String, Object> schema) {
        return normalizeElevenLabsSchema(schema, "tool parameters", true);
    }

    /**
     * ElevenLabs client-tool parameters resemble JSON Schema but use a
     * provider-specific discriminated model. In particular, nested values must
     * declare how they are populated (Sauti uses {@code description}) and
     * ordinary JSON Schema keywords such as {@code additionalProperties},
     * {@code format}, and {@code maxLength} are rejected with HTTP 422.
     *
     * Keep Sauti's canonical schema untouched for other providers and translate
     * only the payload sent to ElevenLabs.
     */
    private Map<String, Object> normalizeElevenLabsSchema(
            Map<?, ?> schema,
            String fieldName,
            boolean root
    ) {
        var type = schemaType(schema);
        var normalized = new LinkedHashMap<String, Object>();
        normalized.put("type", type);

        var description = trim(stringValue(schema.get("description")));
        var format = trim(stringValue(schema.get("format")));
        if (description.isBlank() && !root) {
            description = "Value for " + humanize(fieldName) + ".";
        }
        if (!format.isBlank()) {
            description = (description.isBlank() ? "Value for " + humanize(fieldName) + "." : description)
                    + " Expected format: " + format + ".";
        }
        if (!description.isBlank()) normalized.put("description", description);

        if ("object".equals(type)) {
            var properties = new LinkedHashMap<String, Object>();
            if (schema.get("properties") instanceof Map<?, ?> rawProperties) {
                rawProperties.forEach((key, value) -> {
                    if (key != null && value instanceof Map<?, ?> propertySchema) {
                        var propertyName = key.toString();
                        properties.put(
                                propertyName,
                                normalizeElevenLabsSchema(propertySchema, propertyName, false)
                        );
                    }
                });
            }
            normalized.put("properties", Map.copyOf(properties));
            if (schema.get("required") instanceof List<?> required) {
                var knownRequired = required.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(properties::containsKey)
                        .toList();
                if (!knownRequired.isEmpty()) normalized.put("required", knownRequired);
            }
        } else if ("array".equals(type)) {
            if (schema.get("items") instanceof Map<?, ?> itemSchema) {
                normalized.put(
                        "items",
                        normalizeElevenLabsSchema(itemSchema, fieldName + " item", false)
                );
            } else {
                normalized.put(
                        "items",
                        Map.of(
                                "type", "string",
                                "description", "One value in " + humanize(fieldName) + "."
                        )
                );
            }
        } else if (schema.get("enum") instanceof List<?> values) {
            var allowedValues = values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
            if (!allowedValues.isEmpty()) normalized.put("enum", allowedValues);
        }

        return Map.copyOf(normalized);
    }

    private String schemaType(Map<?, ?> schema) {
        var type = schema.get("type");
        if (type instanceof String value && !value.isBlank()) return value;
        if (type instanceof List<?> values && !values.isEmpty()) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !"null".equals(value))
                    .findFirst()
                    .orElse("string");
        }
        if (schema.containsKey("properties")) return "object";
        if (schema.containsKey("items")) return "array";
        return "string";
    }

    private static String humanize(String value) {
        return trim(value)
                .replace('_', ' ')
                .replaceAll("\\s+", " ");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Map<String, Object> agentBody(
            ManagedVoiceAgentBlueprint blueprint,
            List<String> toolIds,
            boolean includeEndCall
    ) {
        var prompt = new LinkedHashMap<String, Object>();
        prompt.put("prompt", blueprint.instructions() + """

                ELEVENLABS EXECUTION CONTRACT:
                - Call a required business tool before speaking about its result.
                - Treat the returned result as authoritative; never claim an action succeeded unless it did.
                - If a tool is still running, acknowledge the wait naturally and continue automatically when it returns.
                - Keep each spoken answer continuous and concise.
                """);
        prompt.put("temperature", 0.2);
        prompt.put("tool_ids", toolIds);
        if (includeEndCall) {
            prompt.put("built_in_tools", Map.of("end_call", Map.of()));
        }

        var agent = new LinkedHashMap<String, Object>();
        agent.put("first_message", blueprint.greeting());
        agent.put("language", primaryLanguage(blueprint.language()));
        agent.put("prompt", Map.copyOf(prompt));

        var conversationConfig = new LinkedHashMap<String, Object>();
        conversationConfig.put("agent", Map.copyOf(agent));
        conversationConfig.put("turn", Map.of(
                "turn_eagerness", "normal",
                "turn_timeout", 7
        ));
        conversationConfig.put("conversation", Map.of(
                "max_duration_seconds", Math.max(10, blueprint.maxCallDurationSeconds())
        ));

        var body = new LinkedHashMap<String, Object>();
        body.put("name", shorten(blueprint.name(), 100));
        body.put("tags", List.of("sauti-managed"));
        body.put("conversation_config", Map.copyOf(conversationConfig));
        body.put("platform_settings", Map.of(
                "auth", Map.of("enable_auth", true)
        ));
        return Map.copyOf(body);
    }

    private boolean isBuiltInTool(LlmToolDefinition tool) {
        return "end_call".equals(tool.name());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toolIds(ManagedVoiceAgentReference existing) {
        if (existing == null || existing.externalResourcesJson().isBlank()) return Map.of();
        try {
            var resources = objectMapper.readValue(existing.externalResourcesJson(), Map.class);
            var rawToolIds = resources.get("toolIds");
            if (!(rawToolIds instanceof Map<?, ?> map)) return Map.of();
            var result = new LinkedHashMap<String, String>();
            map.forEach((key, value) -> {
                if (key != null && value != null) result.put(key.toString(), value.toString());
            });
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored ElevenLabs agent binding is invalid", exception);
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store ElevenLabs agent binding", exception);
        }
    }

    private static String primaryLanguage(String language) {
        var normalized = trim(language).replace('_', '-').toLowerCase(java.util.Locale.ROOT);
        var separator = normalized.indexOf('-');
        return separator < 0 ? (normalized.isBlank() ? "en" : normalized) : normalized.substring(0, separator);
    }

    private static String path(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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
