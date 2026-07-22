package com.sauti.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.LlmToolCall;
import com.sauti.tool.ToolFulfillmentRouter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VapiWebhookService {
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
        var call = authorizedCall(callSid, token);
        var message = payload == null ? null : payload.path("message");
        if (message == null || message.isMissingNode() || message.isNull()) return Map.of();
        if (!"tool-calls".equals(message.path("type").asText())) return Map.of();

        var results = new ArrayList<Map<String, Object>>();
        var toolCalls = message.path("toolCallList");
        if (!toolCalls.isArray()) return Map.of("error", "Vapi tool request did not include toolCallList");
        for (var node : toolCalls) {
            var function = node.path("function");
            var id = text(node, "id");
            var name = firstNonBlank(text(node, "name"), text(function, "name"));
            var arguments = arguments(node, function);
            var routed = toolRouter.route(call, new LlmToolCall(id, name, arguments));
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
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Vapi callback token is required");
        var principal = tokenService.verify(token);
        if (!callSid.equals(principal.callSid())) throw new IllegalArgumentException("Vapi callback token does not match call");
        return callRepository.findByTwilioCallSid(callSid)
                .filter(Call::isActive)
                .filter(call -> "test".equals(call.getDirection()) || "web".equals(call.getDirection()))
                .filter(call -> principal.publicAgentId().equals(call.getAgent().getId().toString())
                        || principal.publicAgentId().equals(call.getAgent().getWebVoicePublicId()))
                .orElseThrow(() -> new IllegalArgumentException("Vapi call is unavailable"));
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
