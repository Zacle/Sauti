package com.sauti.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.CallSessionStore;
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
    private final CallSessionStore sessions;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedResult> completed = new ConcurrentHashMap<>();

    public ManagedVoiceToolService(
            CallRepository callRepository,
            WebVoiceTokenService tokenService,
            ToolFulfillmentRouter toolRouter,
            CallSessionStore sessions,
            ObjectMapper objectMapper
    ) {
        this.callRepository = callRepository;
        this.tokenService = tokenService;
        this.toolRouter = toolRouter;
        this.sessions = sessions;
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
            var toolCall = new LlmToolCall(invocationId, name, arguments);
            bridgeManagedConfirmation(call, toolCall);
            LlmToolResult routed = toolRouter.route(call, toolCall);
            response = response(routed);
            success = Boolean.TRUE.equals(response.get("success"));
        } catch (RuntimeException exception) {
            success = false;
            LOGGER.warn(
                    "Managed voice tool failed provider={} sautiCallId={} tool={} exception={}",
                    provider, call.getId(), name, exception.getClass().getSimpleName()
            );
            response = Map.of("success", false, "error", "The requested action could not be completed");
        }
        LOGGER.info(
                "Managed voice tool completed provider={} sautiCallId={} tool={} success={} "
                        + "actionPerformed={} status={} durationMs={}",
                provider,
                call.getId(),
                name,
                success,
                response.getOrDefault("actionPerformed", "not_applicable"),
                response.getOrDefault("status", success ? "completed" : "failed"),
                elapsedMillis(startedAt)
        );
        return new CachedResult(
                response,
                java.time.Instant.now().plusSeconds(Math.max(120, call.getAgent().getMaxCallDurationSeconds() + 120L))
        );
    }

    private void bridgeManagedConfirmation(Call call, LlmToolCall toolCall) {
        if (!"confirmed".equals(stringArgument(toolCall, "confirmation_state"))
                || !"ready_for_action".equals(stringArgument(toolCall, "question_handling"))) {
            return;
        }
        var businessArguments = new java.util.LinkedHashMap<>(toolCall.arguments());
        businessArguments.remove("confirmation_state");
        businessArguments.remove("question_handling");
        businessArguments.values().removeIf(java.util.Objects::isNull);
        sessions.recordManagedConfirmation(
                call.getTwilioCallSid(),
                toolCall.name(),
                Map.copyOf(businessArguments)
        );
    }

    private Map<String, Object> response(LlmToolResult routed) {
        if (!routed.success()) {
            return Map.of(
                    "success", false,
                    "actionPerformed", false,
                    "error", routed.error()
            );
        }
        var facts = routed.result();
        var performedFact = facts.get("actionPerformed");
        var actionDeferred = performedFact instanceof Boolean performed && !performed;
        var response = new java.util.LinkedHashMap<String, Object>();
        response.put("success", !actionDeferred);
        response.put("data", facts);
        copyFact(facts, response, "status");
        copyFact(facts, response, "actionPerformed");
        copyFact(facts, response, "effect");
        copyFact(facts, response, "action");
        copyFact(facts, response, "instruction");
        copyFact(facts, response, "bookingFound");
        copyFact(facts, response, "retryField");
        copyFact(facts, response, "capturedBookingNumber");
        copyFact(facts, response, "bookingNumberReadback");
        if (actionDeferred) {
            response.put("error", "No external action was performed");
        }
        return Map.copyOf(response);
    }

    private void copyFact(
            Map<String, Object> source,
            Map<String, Object> target,
            String name
    ) {
        var value = source.get(name);
        if (value != null) target.put(name, value);
    }

    private String stringArgument(LlmToolCall call, String name) {
        var value = call.arguments().get(name);
        return value == null ? "" : value.toString().trim();
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
                Map<String, Object> converted = objectMapper.convertValue(candidate, new TypeReference<>() { });
                return withoutNulls(converted);
            }
            if (candidate.isTextual() && !candidate.asText().isBlank()) {
                try {
                    Map<String, Object> converted = objectMapper.readValue(
                            candidate.asText(), new TypeReference<>() { }
                    );
                    return withoutNulls(converted);
                } catch (Exception exception) {
                    throw new IllegalArgumentException("Managed voice tool arguments are not valid JSON");
                }
            }
        }
        return Map.of();
    }

    private Map<String, Object> withoutNulls(Map<String, Object> values) {
        var result = new java.util.LinkedHashMap<String, Object>();
        values.forEach((key, value) -> {
            var sanitized = withoutNulls(value);
            if (key != null && sanitized != null) result.put(key, sanitized);
        });
        return Map.copyOf(result);
    }

    private Object withoutNulls(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new java.util.LinkedHashMap<String, Object>();
            map.forEach((key, nested) -> {
                var sanitized = withoutNulls(nested);
                if (key != null && sanitized != null) result.put(key.toString(), sanitized);
            });
            return Map.copyOf(result);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::withoutNulls).filter(java.util.Objects::nonNull).toList();
        }
        return value;
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
