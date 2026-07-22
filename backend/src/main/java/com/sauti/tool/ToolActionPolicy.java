package com.sauti.tool;

import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolDefinition;
import com.sauti.llm.LlmToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Generic safety boundary for every tool that changes external state. Business
 * modules may add stricter rules, but cannot bypass this platform policy.
 */
@Component
public class ToolActionPolicy {
    private static final String SAFETY = "Classify the complete latest caller turn semantically in any language. "
            + "Use answer_before_action when a question, condition, hesitation, correction, or information request "
            + "must be resolved first. Use ready_for_action only when nothing customer-facing remains unresolved.";

    @SuppressWarnings("unchecked")
    public LlmToolDefinition decorate(AgentTool tool, LlmToolDefinition definition) {
        var effect = tool.actionEffect();
        if (!effect.isSideEffecting()) return definition;

        var schema = new LinkedHashMap<String, Object>(definition.inputSchema());
        var properties = new LinkedHashMap<String, Object>(
                (Map<String, Object>) schema.getOrDefault("properties", Map.of())
        );
        var required = new ArrayList<String>((List<String>) schema.getOrDefault("required", List.of()));
        properties.put("question_handling", Map.of(
                "type", "string",
                "enum", List.of("ready_for_action", "answer_before_action"),
                "description", SAFETY
        ));
        if (!required.contains("question_handling")) required.add("question_handling");

        if (tool.confirmationPolicy() == ToolConfirmationPolicy.EXPLICIT) {
            properties.put("confirmation_state", Map.of(
                    "type", "string",
                    "enum", List.of("confirmed", "not_confirmed"),
                    "description", "Whether the caller has explicitly and unconditionally confirmed this exact action and its material parameters on the latest resolved turn."
            ));
            if (!required.contains("confirmation_state")) required.add("confirmation_state");
        }
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.copyOf(required));
        return new LlmToolDefinition(
                definition.name(),
                java.util.Objects.requireNonNullElse(definition.description(), "")
                        + " Effect: " + effect.value() + ". " + SAFETY
                        + confirmationInstruction(tool.confirmationPolicy()),
                Map.copyOf(schema)
        );
    }

    public Optional<LlmToolResult> guard(AgentTool tool, LlmToolCall call) {
        var effect = tool.actionEffect();
        if (!effect.isSideEffecting()) return Optional.empty();
        var readiness = argument(call, "question_handling");
        if (!"ready_for_action".equals(readiness)) {
            var reason = "answer_before_action".equals(readiness)
                    ? "unresolved_customer_request" : "action_context_required";
            return Optional.of(deferred(call, tool, reason,
                    "No external action was performed. Resolve the caller's complete latest request first, then wait for a fresh action decision. Do not claim success."));
        }
        if (tool.confirmationPolicy() == ToolConfirmationPolicy.EXPLICIT
                && !"confirmed".equals(argument(call, "confirmation_state"))) {
            return Optional.of(deferred(call, tool, "explicit_confirmation_required",
                    "No external action was performed. Briefly confirm the exact action and its material parameters, then wait for the caller's explicit unconditional approval. Do not claim success."));
        }
        return Optional.empty();
    }

    public LlmToolCall businessCall(LlmToolCall call) {
        var arguments = new LinkedHashMap<>(call.arguments());
        arguments.remove("question_handling");
        arguments.remove("confirmation_state");
        return new LlmToolCall(call.id(), call.name(), Map.copyOf(arguments));
    }

    private LlmToolResult deferred(LlmToolCall call, AgentTool tool, String reason, String instruction) {
        return LlmToolResult.success(call, Map.of(
                "status", "action_deferred",
                "actionPerformed", false,
                "effect", tool.actionEffect().value(),
                "reason", reason,
                "nextAction", "reply",
                "instruction", instruction
        ));
    }

    private String argument(LlmToolCall call, String name) {
        var value = call.arguments().get(name);
        return value == null ? "" : value.toString().trim();
    }

    private String confirmationInstruction(ToolConfirmationPolicy policy) {
        return switch (policy) {
            case NONE -> "";
            case EXPLICIT -> " Explicit confirmation of the exact action and material parameters is required.";
            case VERIFIED_REVIEW -> " The business module's verified review must authorize the commit.";
        };
    }
}
