package com.sauti.tool;

import java.util.Map;
import java.util.Set;

/**
 * The weakest action policy a tool may run with, whatever is stored for it. Every tool that changes
 * something outside the conversation needs the caller's confirmation; this class makes that hold
 * even for a tool saved with a weaker policy through the API or by older code.
 *
 * <ul>
 *   <li>Built-in actions have a fixed minimum effect: an SMS is always communication, a transfer is
 *       always a transfer, a payment request is always financial. They cannot be marked read-only.</li>
 *   <li>A side-effecting tool always needs confirmation; "none" becomes "explicit".</li>
 *   <li>"Verified review" is enforced only by the calendar's booking review. On any other tool it
 *       would mean no check at all, so it becomes "explicit".</li>
 * </ul>
 */
public final class ToolSafetyFloor {
    private static final Map<String, ToolActionEffect> BY_FULFILLMENT = Map.of(
            "sauti_sms", ToolActionEffect.EXTERNAL_COMMUNICATION,
            "call_transfer", ToolActionEffect.TRANSFER
    );
    private static final Map<String, ToolActionEffect> BY_PLATFORM_TOOL = Map.ofEntries(
            Map.entry("book_slot", ToolActionEffect.DATA_WRITE),
            Map.entry("reschedule_booking", ToolActionEffect.DATA_WRITE),
            Map.entry("cancel_booking", ToolActionEffect.DATA_WRITE),
            Map.entry("update_booking", ToolActionEffect.DATA_WRITE),
            Map.entry("update_google_sheet_row", ToolActionEffect.DATA_WRITE),
            Map.entry("call_custom_webhook", ToolActionEffect.DATA_WRITE),
            Map.entry("send_confirmation_sms", ToolActionEffect.EXTERNAL_COMMUNICATION),
            Map.entry("send_whatsapp_message", ToolActionEffect.EXTERNAL_COMMUNICATION),
            Map.entry("request_mpesa_payment", ToolActionEffect.FINANCIAL),
            Map.entry("transfer_to_human", ToolActionEffect.TRANSFER),
            Map.entry("end_call", ToolActionEffect.TERMINAL)
    );
    /** Effects that must never be relabelled as another side effect. */
    private static final Set<ToolActionEffect> EXACT = Set.of(
            ToolActionEffect.FINANCIAL, ToolActionEffect.TRANSFER, ToolActionEffect.TERMINAL
    );
    /** The only fulfillment that implements the verified booking review. */
    static final String REVIEW_FULFILLMENT = "sauti_calendar";

    private ToolSafetyFloor() {
    }

    /** The lowest effect this tool may declare, or null when the owner may choose freely. */
    public static ToolActionEffect minimumEffect(String fulfillmentType, String toolName) {
        if (fulfillmentType == null) return null;
        var byName = sautiFulfillment(fulfillmentType) && toolName != null ? BY_PLATFORM_TOOL.get(toolName) : null;
        return byName != null ? byName : BY_FULFILLMENT.get(fulfillmentType);
    }

    public static ToolActionEffect effect(AgentTool tool) {
        return effect(tool.getFulfillmentType(), tool.getToolName(), tool.actionEffect());
    }

    public static ToolActionEffect effect(String fulfillmentType, String toolName, ToolActionEffect configured) {
        var minimum = minimumEffect(fulfillmentType, toolName);
        if (configured == null) configured = ToolActionEffect.READ_ONLY;
        if (minimum == null) return configured;
        if (!configured.isSideEffecting() || EXACT.contains(minimum)) return minimum;
        return configured;
    }

    public static ToolConfirmationPolicy confirmation(AgentTool tool) {
        return confirmation(tool.getFulfillmentType(), effect(tool), tool.confirmationPolicy());
    }

    public static ToolConfirmationPolicy confirmation(
            String fulfillmentType,
            ToolActionEffect effect,
            ToolConfirmationPolicy configured
    ) {
        if (effect == null || !effect.isSideEffecting()) return ToolConfirmationPolicy.NONE;
        if (configured == null) return ToolConfirmationPolicy.EXPLICIT;
        if (configured == ToolConfirmationPolicy.NONE) return ToolConfirmationPolicy.EXPLICIT;
        if (configured == ToolConfirmationPolicy.VERIFIED_REVIEW && !REVIEW_FULFILLMENT.equals(fulfillmentType)) {
            return ToolConfirmationPolicy.EXPLICIT;
        }
        return configured;
    }

    /** Built-in action names count only on Sauti's own fulfillments; a custom webhook may reuse a name. */
    private static boolean sautiFulfillment(String fulfillmentType) {
        if (fulfillmentType == null) return false;
        return fulfillmentType.startsWith("sauti_") || Set.of("call_transfer", "noop").contains(fulfillmentType);
    }
}
