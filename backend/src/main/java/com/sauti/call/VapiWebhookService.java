package com.sauti.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.tool.ToolFulfillmentRouter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VapiWebhookService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VapiWebhookService.class);
    private final CallRepository callRepository;
    private final WebVoiceTokenService tokenService;
    private final ToolFulfillmentRouter toolRouter;
    private final ObjectMapper objectMapper;

    public VapiWebhookService(
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

    public Map<String, Object> handle(String callSid, String token, JsonNode payload) {
        var message = payload == null ? null : payload.path("message");
        if (message == null || message.isMissingNode() || message.isNull()) return Map.of();
        var messageType = message.path("type").asText();
        if ("end-of-call-report".equals(messageType)) {
            var call = authorizedCall(callSid, token, false);
            logEndOfCallMetrics(call, message);
            return Map.of();
        }
        var call = authorizedCall(callSid, token);
        if (!"tool-calls".equals(messageType)) return Map.of();

        var results = new ArrayList<Map<String, Object>>();
        var toolCalls = message.path("toolCallList");
        if (!toolCalls.isArray()) return Map.of("error", "Vapi tool request did not include toolCallList");
        var providerCallId = message.path("call").path("id").asText("");
        for (var node : toolCalls) {
            var function = node.path("function");
            var id = text(node, "id");
            var name = firstNonBlank(text(node, "name"), text(function, "name"));
            var arguments = arguments(node, function);
            var startedAt = System.nanoTime();
            LlmToolResult routed;
            try {
                routed = toolRouter.route(call, new LlmToolCall(id, name, arguments));
                LOGGER.info(
                        "Vapi tool completed sautiCallId={} providerCallId={} tool={} success={} durationMs={}",
                        call.getId(), providerCallId, name, routed.success(), elapsedMillis(startedAt)
                );
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Vapi tool failed sautiCallId={} providerCallId={} tool={} durationMs={} reason={}",
                        call.getId(), providerCallId, name, elapsedMillis(startedAt), exception.getMessage()
                );
                throw exception;
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("name", name);
            result.put("toolCallId", id);
            try {
                if (routed.success()) {
                    result.put("result", objectMapper.writeValueAsString(Map.of(
                            "success", true,
                            "data", routed.result()
                    )));
                    if ("end_call".equals(name) && Boolean.TRUE.equals(routed.result().get("ended"))) {
                        result.put("metadata", Map.of("sautiEndCall", true));
                    }
                } else {
                    result.put("error", routed.error());
                }
            } catch (Exception exception) {
                result.put("error", "The tool result could not be serialized");
            }
            results.add(Map.copyOf(result));
        }
        return Map.of("results", List.copyOf(results));
    }

    public Call authorizedCall(String callSid, String token) {
        return authorizedCall(callSid, token, true);
    }

    private Call authorizedCall(String callSid, String token, boolean activeRequired) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Vapi callback token is required");
        var principal = tokenService.verify(token);
        if (!callSid.equals(principal.callSid())) throw new IllegalArgumentException("Vapi callback token does not match call");
        return callRepository.findByTwilioCallSid(callSid)
                .filter(call -> !activeRequired || call.isActive())
                .filter(call -> "test".equals(call.getDirection()) || "web".equals(call.getDirection()))
                .filter(call -> principal.publicAgentId().equals(call.getAgent().getId().toString())
                        || principal.publicAgentId().equals(call.getAgent().getWebVoicePublicId()))
                .orElseThrow(() -> new IllegalArgumentException("Vapi call is unavailable"));
    }

    private void logEndOfCallMetrics(Call call, JsonNode message) {
        var providerCallId = message.path("call").path("id").asText("");
        var artifact = message.path("artifact");
        if (artifact.isMissingNode() || artifact.isNull()) artifact = message.path("call").path("artifact");
        var metrics = artifact.path("performanceMetrics");
        LOGGER.info(
                "Vapi call metrics sautiCallId={} providerCallId={} endedReason={} turnLatencyAverage={} modelLatencyAverage={} voiceLatencyAverage={} transcriberLatencyAverage={} endpointingLatencyAverage={} userInterruptions={} assistantInterruptions={}",
                call.getId(),
                providerCallId,
                message.path("endedReason").asText(message.path("call").path("endedReason").asText("")),
                metric(metrics, "turnLatencyAverage"),
                metric(metrics, "modelLatencyAverage"),
                metric(metrics, "voiceLatencyAverage"),
                metric(metrics, "transcriberLatencyAverage"),
                metric(metrics, "endpointingLatencyAverage"),
                metric(metrics, "numUserInterrupted"),
                metric(metrics, "numAssistantInterrupted")
        );
    }

    private double metric(JsonNode metrics, String name) {
        return metrics.path(name).isNumber() ? metrics.path(name).asDouble() : -1;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private Map<String, Object> arguments(JsonNode node, JsonNode function) {
        for (var candidate : List.of(
                node.path("arguments"),
                node.path("parameters"),
                function.path("arguments"),
                function.path("parameters")
        )) {
            if (candidate.isObject()) {
                return objectMapper.convertValue(candidate, new TypeReference<>() { });
            }
        }
        var raw = firstNonBlank(text(node, "arguments"), text(function, "arguments"));
        if (raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Vapi tool arguments are not valid JSON", exception);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        var value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }
}
