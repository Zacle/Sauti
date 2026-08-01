package com.sauti.llm;

import com.sauti.tool.AgentTool;
import java.util.Map;

public record LlmToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema,
        boolean callerWaitExpected
) {
    public LlmToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        this(name, description, inputSchema, false);
    }

    public static LlmToolDefinition from(AgentTool tool) {
        return new LlmToolDefinition(
                tool.getToolName(),
                tool.getToolDescription(),
                tool.getParametersSchema(),
                callerWaitExpected(tool)
        );
    }

    private static boolean callerWaitExpected(AgentTool tool) {
        // Even database-first booking operations can create a noticeable voice
        // pause while the managed model selects, invokes, and consumes a tool
        // result. Mark customer-facing workflow operations so Telnyx can cover
        // that perceived wait. Static facts remain on the silent fast path.
        return switch (tool.getToolName()) {
            case "check_availability", "lookup_booking", "book_slot", "update_booking",
                    "reschedule_booking", "cancel_booking",
                    "send_confirmation_sms", "transfer_to_human",
                    "send_whatsapp_message", "lookup_google_sheet_row", "update_google_sheet_row",
                    "request_mpesa_payment", "check_mpesa_payment", "call_custom_webhook" -> true;
            default -> "webhook".equals(tool.getFulfillmentType());
        };
    }
}
