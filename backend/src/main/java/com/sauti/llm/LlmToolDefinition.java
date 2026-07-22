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
        // Purely local conversational state and static schedule reads should
        // return before a filler can help. Calendar, integration, webhook,
        // communication, transfer, and write operations may cross a network or
        // transaction boundary, so managed runtimes should acknowledge them.
        return !"noop".equals(tool.getFulfillmentType())
                && !"get_business_hours".equals(tool.getToolName());
    }
}
