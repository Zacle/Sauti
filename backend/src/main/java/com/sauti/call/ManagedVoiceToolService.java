package com.sauti.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.tool.ToolFulfillmentRouter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provider-neutral callback for managed voice webhook tools.
 *
 * Every request is authorized by the same short-lived, call-scoped token used
 * by web voice. The provider can invoke only tools enabled for the bound agent.
 * Results are cached per invocation fingerprint to make provider redelivery
 * safe for mutating tools during browser tests.
 */
@Service
public class ManagedVoiceToolService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedVoiceToolService.class);
    private static final Set<String> PROVIDERS = Set.of("retell", "elevenlabs", "telnyx");
    private final CallRepository callRepository;
    private final WebVoiceTokenService tokenService;
    private final ToolFulfillmentRouter toolRouter;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedResult> completed = new ConcurrentHashMap<>();

    public ManagedVoiceToolService(
            CallRepository callRepository,
            WebVoiceTokenService tokenService,
            ToolFulfillmentRouter toolRouter,
            ObjectMapper objectMapper
    ) {
        this.callRepository = callRepository;
        this.tokenService = tokenService;
        this.toolRouter = toolRouter;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> execute(String provider, String callSid, String token, JsonNode payload) {
        var normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (!PROVIDERS.contains(normalizedProvider)) {
            throw new IllegalArgumentException("Unsupported managed voice provider");
        }
        var call = authorizedCall(callSid, token);
        var name = toolName(payload);
        if (!name.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Managed voice tool name is invalid");
        }
        var arguments = arguments(payload);
        var invocationId = invocationId(normalizedProvider, payload, name, arguments);
        var cacheKey = call.getId() + ":" + invocationId;
        purgeExpired();
        return completed.compute(cacheKey, (key, existing) -> {
            if (existing != null && java.time.Instant.now().isBefore(existing.expiresAt())) return existing;
            return executeOnce(normalizedProvider, call, invocationId, name, arguments);
        }).result();
    }

    private CachedResult executeOnce(
            String provider,
            Call call,
            String invocationId,
            String name,
            Map<String, Object> arguments
    ) {
        var startedAt = System.nanoTime();
        Map<String, Object> response;
        boolean success;
        try {
            LlmToolResult routed = toolRouter.route(call, new LlmToolCall(invocationId, name, arguments));
            success = routed.success();
            response = routed.success()
                    ? Map.of("success", true, "data", routed.result())
                    : Map.of("success", false, "error", routed.error());
        } catch (RuntimeException exception) {
            success = false;
            response = Map.of("success", false, "error", "The requested action could not be completed");
        }
        LOGGER.info(
                "Managed voice tool completed provider={} sautiCallId={} tool={} success={} durationMs={}",
                provider, call.getId(), name, success, elapsedMillis(startedAt)
        );
        return new CachedResult(
                response,
                java.time.Instant.now().plusSeconds(Math.max(120, call.getAgent().getMaxCallDurationSeconds() + 120L))
        );
    }

    private Call authorizedCall(String callSid, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Managed voice callback token is required");
        }
        var principal = tokenService.verify(token);
        if (!callSid.equals(principal.callSid())) {
            throw new IllegalArgumentException("Managed voice callback token does not match call");
        }
        return callRepository.findByTwilioCallSid(callSid)
                .filter(Call::isActive)
                .filter(call -> "test".equals(call.getDirection()) || "web".equals(call.getDirection()))
                .filter(call -> principal.publicAgentId().equals(call.getAgent().getId().toString())
                        || principal.publicAgentId().equals(call.getAgent().getWebVoicePublicId()))
                .orElseThrow(() -> new IllegalArgumentException("Managed voice call is unavailable"));
    }

    private String toolName(JsonNode payload) {
        return firstNonBlank(
                text(payload, "name"),
                text(payload, "tool_name"),
                text(payload == null ? null : payload.path("function"), "name")
        );
    }

    private Map<String, Object> arguments(JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) return Map.of();
        for (var candidate : List.of(
                payload.path("args"),
                payload.path("arguments"),
                payload.path("parameters"),
                payload.path("function").path("arguments"),
                payload.path("function").path("parameters")
        )) {
            if (candidate.isObject()) {
                return objectMapper.convertValue(candidate, new TypeReference<>() { });
            }
            if (candidate.isTextual() && !candidate.asText().isBlank()) {
                try {
                    return objectMapper.readValue(candidate.asText(), new TypeReference<>() { });
                } catch (Exception exception) {
                    throw new IllegalArgumentException("Managed voice tool arguments are not valid JSON");
                }
            }
        }
        return Map.of();
    }

    private String invocationId(
            String provider,
            JsonNode payload,
            String name,
            Map<String, Object> arguments
    ) {
        var explicit = firstNonBlank(
                text(payload, "tool_call_id"),
                text(payload, "call_id"),
                text(payload, "id")
        );
        if (!explicit.isBlank()) return provider + ":" + explicit + ":" + name;
        var providerCallId = text(payload == null ? null : payload.path("call"), "call_id");
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(
                    objectMapper.writeValueAsString(Map.of(
                            "providerCallId", providerCallId,
                            "name", name,
                            "arguments", arguments
                    )).getBytes(StandardCharsets.UTF_8)
            );
            return provider + ":" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to identify the managed voice tool invocation", exception);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        var value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private void purgeExpired() {
        var now = java.time.Instant.now();
        completed.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private record CachedResult(Map<String, Object> result, java.time.Instant expiresAt) {
    }
}
