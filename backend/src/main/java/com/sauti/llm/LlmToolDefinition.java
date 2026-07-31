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
        // Sauti booking mutations commit locally and queue external writes, so
        // they should return before a filler can help. Availability and remote
        // integration/communication actions can cross a network boundary.
        return switch (tool.getToolName()) {
            case "check_availability", "send_confirmation_sms", "transfer_to_human",
                    "send_whatsapp_message", "lookup_google_sheet_row", "update_google_sheet_row",
                    "request_mpesa_payment", "check_mpesa_payment", "call_custom_webhook" -> true;
            default -> "webhook".equals(tool.getFulfillmentType());
        };
    }
}
