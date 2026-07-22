package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolDefinition;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.CallSessionStore;
import com.sauti.session.ConversationState;
import com.sauti.session.PendingAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CallSessionStore sessions;

    @Autowired
    public ToolActionPolicy(CallSessionStore sessions) {
        this.sessions = sessions;
    }

    /** Schema-only constructor for tests that do not execute an action. */
    public ToolActionPolicy() {
        this.sessions = null;
    }

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
                    "description", tool.actionEffect() == ToolActionEffect.TERMINAL
                            ? "Whether the caller clearly indicated they are finished after the agent's respectful farewell."
                            : "Semantic claim that the caller explicitly and unconditionally confirmed this exact action and its material parameters. The server independently verifies this against a retained proposal and a later conversation-state revision; this field alone never authorizes the action."
            ));
            if (!required.contains("confirmation_state")) required.add("confirmation_state");
        }
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.copyOf(required));
        return new LlmToolDefinition(
                definition.name(),
                java.util.Objects.requireNonNullElse(definition.description(), "")
                        + " Effect: " + effect.value() + ". " + SAFETY
                        + confirmationInstruction(tool.confirmationPolicy(), effect),
                Map.copyOf(schema),
                definition.callerWaitExpected()
        );
    }

    public Optional<LlmToolResult> guard(Call businessCall, AgentTool tool, LlmToolCall call) {
        var effect = tool.actionEffect();
        if (!effect.isSideEffecting()) return Optional.empty();
        var readiness = argument(call, "question_handling");
        if (!"ready_for_action".equals(readiness)) {
            var reason = "answer_before_action".equals(readiness)
                    ? "unresolved_customer_request" : "action_context_required";
            return Optional.of(deferred(call, tool, reason,
                    "No external action was performed. Resolve the caller's complete latest request first, then wait for a fresh action decision. Do not claim success."));
        }
        if (tool.confirmationPolicy() == ToolConfirmationPolicy.EXPLICIT) {
            if (effect == ToolActionEffect.TERMINAL) {
                if (!"confirmed".equals(argument(call, "confirmation_state"))) {
                    return Optional.of(deferred(call, tool, "explicit_confirmation_required",
                            "No terminal action was performed. End only when the caller clearly indicates they are finished, and speak one respectful farewell first."));
                }
                return Optional.empty();
            }
            var proposedArguments = businessCall(call).arguments();
            var state = conversationState(businessCall);
            var approvedOnLaterTurn = "confirmed".equals(argument(call, "confirmation_state"))
                    && sessions != null
                    && sessions.consumeConfirmedAction(
                            businessCall.getTwilioCallSid(), call.name(), proposedArguments
                    );
            if (!approvedOnLaterTurn) {
                rememberProposal(businessCall, call.name(), proposedArguments, state.revision());
                return Optional.of(deferred(call, tool, "verified_confirmation_required",
                        "No external action was performed. This exact action is now retained by the server. "
                                + "Briefly review its material parameters and ask for explicit unconditional confirmation, then stop. "
                                + "On the caller's next turn, interpret the answer with update_conversation_state. Do not claim success."));
            }
        }
        return Optional.empty();
    }

    public LlmToolResult factualOutcome(AgentTool tool, LlmToolResult result) {
        if (!tool.actionEffect().isSideEffecting()) return result;
        var facts = new LinkedHashMap<String, Object>(result.result());
        facts.putIfAbsent("actionPerformed", actionPerformed(result));
        facts.putIfAbsent("effect", tool.actionEffect().value());
        facts.putIfAbsent("action", tool.getToolName());
        return new LlmToolResult(
                result.toolCallId(), result.name(), result.success(), Map.copyOf(facts), result.error()
        );
    }

    private boolean actionPerformed(LlmToolResult result) {
        if (!result.success()) return false;
        for (var key : List.of(
                "bookingCreated", "updated", "cancelled", "sent", "requested", "transferred", "ended"
        )) {
            var value = result.result().get(key);
            if (value instanceof Boolean fact) return fact;
        }
        // A successful custom fulfillment without a more specific factual flag
        // means the configured provider accepted the action.
        return true;
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

    private String confirmationInstruction(ToolConfirmationPolicy policy, ToolActionEffect effect) {
        return switch (policy) {
            case NONE -> "";
            case EXPLICIT -> effect == ToolActionEffect.TERMINAL
                    ? " The caller must clearly indicate that they are finished before ending the call."
                    : " Explicit confirmation of the exact action and material parameters is required and is verified against a server-retained proposal from an earlier caller turn.";
            case VERIFIED_REVIEW -> " The business module's verified review must authorize the commit.";
        };
    }

    private ConversationState conversationState(Call call) {
        if (sessions == null || call == null) return ConversationState.empty();
        return sessions.conversationState(call.getTwilioCallSid()).orElse(ConversationState.empty());
    }

    private void rememberProposal(Call call, String toolName, Map<String, Object> arguments, long revision) {
        if (sessions == null || call == null || call.getTwilioCallSid() == null) return;
        sessions.updatePendingAction(
                call.getTwilioCallSid(), new PendingAction(toolName, arguments, revision)
        );
    }
}
